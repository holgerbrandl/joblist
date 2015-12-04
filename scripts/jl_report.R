#!/usr/bin/env Rscript

devtools::source_url("https://raw.githubusercontent.com/holgerbrandl/datautils/v1.20/R/core_commons.R")
devtools::source_url("https://raw.githubusercontent.com/holgerbrandl/datautils/v1.20/R/ggplot_commons.R")

require_auto(lubridate)
require_auto(DT)


if(!exists("reportName")){
    argv = commandArgs(TRUE)

    if(length(argv) == 0){
        reportName=".jobs"
        # reportName=".ipsjobs"
        # reportName=".trinjob"
        # reportName=".blastn"
        # reportName=".failchunksblastx"
    #    stop("Usage: RemoveContaminants.R <assemblyBaseName>")
    }else{
        reportName=argv[1]
    }
}
# setwd("/Volumes/projects/plantx/inprogress/stowers/dd_Pgra_v4/bac_contamination"); reportName=".blastn"
# setwd("/projects/plantx/inprogress/stowers/dd_Pgra_v4/bac_contamination"); reportName=".blastn"

reportNiceName <- reportName %>% basename() %>% str_replace_all("^[.]", "")
#' # Job Report:  `r reportNiceName`

#' Working Directory: `r normalizePath(reportName)`

stopifnot(file.exists(reportName))


## todo remove redefinition once core_commons_v1.21 has been released
safe_ifelse <- function(cond, yes, no) {
  class.y <- class(yes)
  if ("factor" %in% class.y) {  # Note the small condition change here
    levels.y = levels(yes)
  }
  X <- ifelse(cond,yes,no)
  if ("factor" %in% class.y) {  # Note the small condition change here
    X = as.factor(X)
    levels(X) = levels.y
  } else {
    class(X) <- class.y
  }
  return(X)
}

## todo remove redefinition once core_commons_v1.21 has been released
empty_as_na <- function(x) safe_ifelse(x!="", x, NA)


allJobs <- read.delim(paste0(reportName, ".stats.runinfo.log"), fill=T) %>%
    transform(job_id=factor(job_id)) %>%
    arrange(job_id) %>%
    # impose an order on th ejob ids
    mutate_each(funs(empty_as_na)) %>%
    mutate(job_id=reorder(job_id, as.numeric(job_id)), resubmission_of=factor(resubmission_of))


## parse the dates
#parse_date_time(ac("00:00:00.00"), c("%d:%H:%M.%S"))
#parse_date_time(ac("00:04:55.18"), c("%d:%H%M%S"))
allJobs %<>% mutate_each(funs(ymd_hms), submit_time, start_time, finish_time)

allJobs %<>% mutate(pending_time=difftime(start_time, submit_time,  units="secs"), pending_time_min=as.numeric(pending_time)/60)
allJobs %<>% mutate(exec_time=difftime(finish_time, start_time, units="secs"), exec_time_min=as.numeric(exec_time)/60, exec_time_hours=as.numeric(exec_time)/3600)


## extract multi-threading number
allJobs %<>% mutate(num_cores=str_match(exec_host, "([0-9]+)[*]n")[,2]) %>% mutate(num_cores=ifelse(is.na(num_cores), 1, num_cores))

## add the queue limits
## todo read this from config or guess from scheduler
wallLimits <- c(short=1, medium=8, long=96)
allJobs %<>%  mutate(queueLimit=wallLimits[ac(queue)])
allJobs %<>% mutate(exceeded_queue_limit=exec_time_hours>queueLimit)

write.delim(allJobs, file=paste0(reportName, ".stats.runinfo_ext.log"))


# Extract the final set (not including the killed and resubmitted ones)
jobs <- allJobs %>%
    group_by(job_name) %>%
    arrange(desc(submit_time)) %>%
    slice(1) %>% ungroup

#jcLogsFile <- paste0(reportName, ".stats.jc.log")
#if(file.exists(jcLogsFile)){ tbd...

if(unlen(jobs$exec_host)<50){
    jobs %>% ggplot(aes(exec_host)) + geom_bar() + coord_flip()
}


if(nrow(jobs)==0){
    system(paste("mailme 'no jobs were run in  ",normalizePath(reportName),"'"))
    warning(paste("no jobs were run in  ",normalizePath(reportName)))
    stop()
}


#ggplot(jobs, aes(pending_time_min)) + geom_histogram() + ggtitle("pending times") + coord_flip()
if(nrow(jobs)<50){
    ggplot(jobs, aes(reorder(job_id, -as.numeric(job_id)), pending_time_min/60)) + geom_bar(stat="identity") + ggtitle("pending times") + coord_flip() + xlab("job id")
}else{
    ggplot(jobs, aes(as.numeric(job_id), pending_time_min/60)) + geom_area() + ggtitle("pending times")+xlab("job_nr") + ylab("pending time [h]")
}
#ggsave2(p=reportName)

if(nrow(jobs)<50){
    ggplot(jobs, aes(reorder(job_id, -as.numeric(job_id)), exec_time_hours)) + geom_bar(stat="identity") + ggtitle("job execution times") + coord_flip() + xlab("job id")
}else{
    ggplot(jobs, aes(as.numeric(job_id), exec_time_hours))  + geom_area() + ggtitle("job execution times")+ xlab("job_nr") + geom_hline(mapping=aes(yintercept=queueLimit), color="red")
}

#ggplot(jobs, aes(as.numeric(job_idx), exec_time_min/pending_time_min)) + geom_area() + ggtitle("pending vs exec time ratio")+xlab("job_nr")
ggplot(jobs, aes(exec_time_min, pending_time_min)) + geom_point() + ggtitle("pending vs exec time") + geom_abline()


# jobs <- read.delim("jobs.txt")

#require_auto(knitr)
jobs %>%
    mutate(pending_time_hours=pending_time_min/60) %>%
    select(job_id, status, exec_host, job_name, exec_time_hours) %>%
    datatable() %>%
    formatRound('exec_time_hours', 3) # see https://rstudio.github.io/DT/functions.html


#######################################################################################################################
#' # Resubmission Statistics

#' In total there were `r filter(allJobs, !is.na(resubmission_of)) %>% nrow` job resubmissions
resubmissions <- allJobs %>%
    filter(!is.na(resubmission_of)) %>%
#    semi_join(allJobs, c("job_id"="resubmission_of")) %>%
    transmute(job_id, status, exec_time_hours, job_name, resubmission_of)

resubmissions %>% datatable()

## todo link log files and configuration xmls into tables where possible
