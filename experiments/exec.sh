#!/bin/sh
#time crf_learn -c 4.0 template ../data/SMSNP.conll.train crfpp.alldata.4.0.model
#time (crf_test -m crfpp.alldata.4.0.model ../data/SMSNP.conll.test > crfpp.alldata.4.0.result)

#time crf_learn -c 4.0 template ../data/SMSNP.conll.singleO.train crfpp.alldata.4.0.singleO.model
#time (crf_test -m crfpp.alldata.4.0.singleO.model ../data/SMSNP.conll.singleO.test > crfpp.alldata.4.0.singleO.result)

#time crf_learn -c 4.0 template ../data/SMSNP.conll.train.5000 crfpp.5000.4.0.model
#time (crf_test -m crfpp.5000.4.0.model ../data/SMSNP.conll.test > crfpp.5000.4.0.result)

time crf_learn -c 4.0 template ../data/SMSNP.conll.train.500 crfpp.500.4.0.model
time (crf_test -m crfpp.500.4.0.model ../data/SMSNP.conll.test > crfpp.500.4.0.result)
