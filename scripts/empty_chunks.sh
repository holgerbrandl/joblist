mkdir  ~/jl_tester
cd  ~/jl_tester

echo "
    ## empty chunk
    ## test2
    echo test
" | jl submit --batch - --bsep '##'

echo "
    ## empty chunk
    ## test2
    echo test
" > /Users/brandl/Dropbox/cluster_sync/joblist/test_data/empty_chunks.txt
