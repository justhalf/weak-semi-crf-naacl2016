# The script to generate brown clusters features
# This requires the brown cluster binary "wcluster" to be present in the folder "brown-cluster"
# The Brown clustering software can be downloaded at:
# https://github.com/percyliang/brown-cluster
# Note that the clustering result might be different, and so in this package we include the clustering that was used
# in the experiments for the paper

# Use the normal regex-tokenized corpus
# The output of this one is at smsCorpus_en_2015.03.09_all.regex-c100-p1.out
#brown-cluster/wcluster --text data/smsCorpus_en_2015.03.09_all.regex.txt --c 100 --threads 4

# Use the normal regex-tokenized corpus with gold-tokenized training and dev data appended at the end
# The output of this one is at 63-c100-p1.out
# If the name is different, rename it into 63-c100-p1.out
# The file SMSNP.conll.regex.gold.{train,dev} were created by running com.statnlp.experiment.smsnp.SMSNPTokenizer
java -cp target/experiments-smsnp-1.0-SNAPSHOT.jar \
    com.statnlp.experiment.smsnp.SMSNPTokenizer \
    -tokenizer regex \
    -input data/smsCorpus_en_2015.03.09_all.txt \
    -output data/smsCorpus_en_2015.03.09_all.regex.txt
brown-cluster/wcluster --text <(cut -d ' ' -f5- data/smsCorpus_en_2015.03.09_all.regex.txt; cut -d ' ' -f1 data/SMSNP.conll.regex.gold.train data/SMSNP.conll.regex.gold.dev | perl -n0e 's/(?<!\n)\n(?!(\n|$))/ /g; s/\n\n/\n/g; print;') --c 100 --threads 4
