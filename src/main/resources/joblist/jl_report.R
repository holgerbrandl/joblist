#!/usr/bin/env Rscript

devtools::source_url("https://raw.githubusercontent.com/holgerbrandl/datautils/v1.22/R/core_commons.R")
devtools::source_url("https://raw.githubusercontent.com/holgerbrandl/datautils/v1.22/R/ggplot_commons.R")

require_auto(lubridate)
require_auto(DT)


if(!exists("joblist")){
    argv = commandArgs(TRUE)

    if(length(argv) == 0){
        joblist=".jobs"
        # joblist=".ipsjobs"
        # joblist=".trinjob"
        # joblist=".blastn"
        # joblist=".failchunksblastx"
    #    stop("Usage: RemoveContaminants.R <assemblyBaseName>")
    }else{
        joblist=argv[1]
    }
}
# setwd("/Volumes/projects/plantx/inprogress/stowers/dd_Pgra_v4/bac_contamination"); joblist=".blastn"
# setwd("/projects/plantx/inprogress/stowers/dd_Pgra_v4/bac_contamination"); joblist=".blastn"

reportNiceName <- joblist %>% basename() %>% str_replace_all("^[.]", "")
#' # JobList Report:  `r reportNiceName`

#' Working Directory: `r normalizePath(joblist)`

stopifnot(file.exists(joblist))


#' ## Summary
#+ results='asis'
cat(system(paste("jl status", joblist), intern=T), sep='<br>')


#' ## Job Example
#+ results='asis'
cat(paste("<code>", system(paste("jl status --first --log cmd --no_header", joblist), intern=T), "</code>"), sep='<br>')

#' ## Job Statistics

#+
allJobs <- read.delim(paste0(joblist, ".runinfo.log"), fill=T) %>%
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

write.delim(allJobs, file=paste0(joblist, ".runinfo_ext.log"))


# Extract the final set (not including the killed and resubmitted ones)
jobs <- allJobs %>%
    group_by(job_name) %>%
    arrange(desc(submit_time)) %>%
    slice(1) %>% ungroup

#' Total Jobs: `r nrow(jobs)`


#jcLogsFile <- paste0(joblist, ".stats.jc.log")
#if(file.exists(jcLogsFile)){ tbd...

if(unlen(jobs$exec_host)<50 & unlen(jobs$exec_host) >1){
    jobs %>% ggplot(aes(exec_host)) + geom_bar() + coord_flip()
}


if(nrow(jobs)==0){
    system(paste("mailme 'no jobs were run in  ",normalizePath(joblist),"'"))
    warning(paste("no jobs were run in  ",normalizePath(joblist)))
    stop()
}

## status overview
jobs %>% ggplot(aes(status)) + geom_bar() + xlab("job status")


startedJobs = filter(jobs, !is.na(pending_time_min))
finishedJobs = filter(jobs, !is.na(exec_time_hours))

#' num started jobs is `r nrow(startedJobs)`

#+ eval=nrow(startedJobs)>0
if(nrow(jobs)<50){
    ggplot(jobs, aes(reorder(job_id, -as.numeric(job_id)), pending_time_min/60)) +
    geom_bar(stat="identity") + ggtitle("pending times") +
    coord_flip() +
    xlab("job id") +
    ylab("pending time [h]")
}else{
    ggplot(jobs, aes(as.numeric(job_id), pending_time_min/60)) +
    geom_area() +
    ggtitle("pending times") +
    xlab("job_nr") +
    ylab("pending time [h]")
}

#+ eval=nrow(finishedJobs)>0
if(nrow(jobs)<50){
    ggplot(jobs, aes(reorder(job_id, -as.numeric(job_id)), exec_time_hours)) + geom_bar(stat="identity") + ggtitle("job execution times") + coord_flip() + xlab("job id")
}else{
    ggplot(jobs, aes(as.numeric(job_id), exec_time_hours))  + geom_area() + ggtitle("job execution times")+ xlab("job_nr") + geom_hline(mapping=aes(yintercept=queueLimit), color="red")
}

#+ pend_vs_exec, eval=nrow(finishedJobs)>0
#ggplot(jobs, aes(as.numeric(job_idx), exec_time_min/pending_time_min)) + geom_area() + ggtitle("pending vs exec time ratio")+xlab("job_nr")
ggplot(jobs, aes(exec_time_min, pending_time_min)) + geom_point() + ggtitle("pending vs exec time") + geom_abline()


# jobs <- read.delim("jobs.txt")

#require_auto(knitr)
#+
jobs %>%
    mutate(pending_time_hours=pending_time_min/60) %>%
    select(job_id, status, exec_host, job_name, exec_time_hours) %>%
    datatable() %>%
    formatRound('exec_time_hours', 3) # see https://rstudio.github.io/DT/functions.html


#######################################################################################################################
#' ## Resubmitted Jobs

resubmissions <- allJobs %>%
    filter(!is.na(resubmission_of)) %>%
    #    semi_join(allJobs, c("job_id"="resubmission_of")) %>%
    transmute(job_id, status, exec_time_hours, job_name, resubmission_of)

#' In total there were `r filter(allJobs, !is.na(resubmission_of)) %>% nrow` job resubmissions

resubmissions %>% datatable()

## todo link log files and configuration xmls into tables where possible

#' Made with [jl](https://github.com/holgerbrandl/joblist)