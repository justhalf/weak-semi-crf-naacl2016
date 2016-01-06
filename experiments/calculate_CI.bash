trap exit SIGINT
for wordBased in "" "-wordBased"; do
    for file in $(ls -1 final_experiments); do
        echo -n "$file ";
        java -cp ../target/experiments-smsnp-1.0-SNAPSHOT.jar com.statnlp.experiment.smsnp.SMSNPEvaluator \
            final_experiments/$file \
            -testFile ../data/SMSNP.test \
            -resample \
            -n 1000 \
            -tokenized \
            ${wordBased}
    done
done
