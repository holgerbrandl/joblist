#!/usr/bin/env Rscript

devtools::source_url("https://raw.githubusercontent.com/holgerbrandl/datautils/v1.20/R/core_commons.R")
devtools::source_url("https://raw.githubusercontent.com/holgerbrandl/datautils/v1.20/R/ggplot_commons.R")

require_auto(lubridate)


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

reportNiceName <-
 str_replace_all(reportName, "^[.]", "")
#' # Job Report:  `r reportNiceName`

stopifnot(file.exists(reportName))

echo("processing job report for '", reportName,"'")

allJobs <- read.delim(paste0(reportName, ".stats.runinfo.log"), fill=T) %>%
    transform(job_id=factor(job_id)) %>%
    arrange(job_id) %>%
    # impose an order on th ejob ids
    mutate(job_id=reorder(job_id, as.numeric(job_id)), resubmitted_as=factor(resubmitted_as))


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



# Extract the final set (not including the killed and resubmitted ones)
jobs <- allJobs %>% filter(is.na(resubmitted_as))


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


write.delim(jobs, file=paste0(reportName, ".stats.runinfo_ext.log"))
# jobs <- read.delim("jobs.txt")

#require_auto(knitr)
require_auto(DT)
jobs %>% mutate(pending_time_hours=pending_time_min/60) %>% select(job_id, exec_host, job_name, pending_time_hours, exec_time_hours) %>% datatable()


#######################################################################################################################
#' # Resubmission Statistics

#' In total there were `r filter(allJobs, !is.na(resubmitted_as)) %>% nrow` job resubmissions
resubmissions <- allJobs %>%
    semi_join(allJobs, c("job_id"="resubmitted_as")) %>%
 select(resubmitted_as, job_id, job_name)

resubmissions %>% datatable()
