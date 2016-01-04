# Use the normal regex-tokenized corpus
# The output of this one is at smsCorpus_en_2015.03.09_all.regex-c100-p1.out
brown-cluster/wcluster --text data/smsCorpus_en_2015.03.09_all.regex.txt --c 100 --threads 4
# Use the normal regex-tokenized corpus with gold-tokenized training and dev data appended at the end
# The output of this one is at 63-c100-p1.out
brown-cluster/wcluster --text <(cut -d ' ' -f5- data/smsCorpus_en_2015.03.09_all.regex.txt; cut -d ' ' -f1 data/SMSNP.conll.regex.gold.train data/SMSNP.conll.regex.gold.dev | perl -n0e 's/(?<!\n)\n(?!(\n|$))/ /g; s/\n\n/\n/g; print;') --c 100 --threads 4
