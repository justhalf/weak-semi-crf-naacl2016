trap exit SIGINT
for wordBased in "" "-wordBased"; do
    if [ -n "${wordBased}" ]; then
        echo "Word-based";
    else
        echo "Character-based";
    fi
    for file in $(ls -1 final_experiments_v2/*.result); do
        echo -n "$file ";
        java -cp ../target/experiments-smsnp-1.0-SNAPSHOT.jar com.statnlp.experiment.smsnp.SMSNPEvaluator \
            $file \
            -testFile ../data/SMSNP.test \
            -resample \
            -n 1000 \
            -tokenized \
            ${wordBased}
    done
done
