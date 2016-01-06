# To run the experiments on full dataset, using CRF++ and our LinearCRF
dry_run=true  # Set this to true to just print the experiments that are going to be executed
retrain_existing=true  # If the model with the same name is present, no retraining is done, unless this value is true
retest_existing=false  # If the model with the same name is present, no retesting is done, unless this value is true

experiment_dir="experiments/full_dataset" # The directory to store all the results
lcrf_features="word,transition,brown_cluster" # The base features used by LCRF
do_tokenized_gold=true # Whether to use gold-tokenized data as the input (to investigate upper bound given ideal tokenization)

base_train_path="data/SMSNP.train" # The training file
base_test_path="data/SMSNP"  # The base filename for test files. This will be appended with ".dev" or ".test"
brown_path="63-c100-p1.out/paths" # The Brown clustering file
if $dry_run; then
    echo "Running in dry run mode"
fi
algo="crfpp"

for tokenizer in "regex"; do # "whitespace"; do
    for use_gold in ""; do # "-useGoldTokenization"; do
        for test_file in "dev"; do # "test"; do
            base_experiment_name="${algo}.${tokenizer}.${test_file}"
            base_model_name="${algo}.${tokenizer}"
            if [ -n "${use_gold}" ]; then
                base_experiment_name="${base_experiment_name}.gold_tokenized"
                base_model_name="${base_model_name}.gold_tokenized"
            fi
            templates=("-templatePath experiments/template-base")
            templates+=("-templatePath experiments/template-2bef")
            templates+=("-templatePath experiments/template-full")
            templates+=("-templatePath experiments/template-base-brown")
            for template_idx in 0 1 2 3; do
                template=${templates[template_idx]}
                if [ $template_idx -eq 1 ]; then
                    experiment_name="${base_experiment_name}.2bef"
                    model_name="${base_model_name}.2bef"
                elif [ $template_idx -eq 2 ]; then
                    experiment_name="${base_experiment_name}.full"
                    model_name="${base_model_name}.full"
                elif [ $template_idx -eq 3 ]; then
                    experiment_name="${base_experiment_name}.brown"
                    model_name="${base_model_name}.brown"
                else
                    experiment_name="${base_experiment_name}"
                    model_name="${base_model_name}"
                fi
                if [ -a "${experiment_dir}/${model_name}.model" ] && ! ${retrain_existing}; then
                    if ${retest_existing}; then
                        train_path=""
                    else
                        continue
                    fi
                else
                    train_path="-trainPath ${base_train_path}"
                fi
                echo ${experiment_name} ${template} ${use_gold} ${train_path} ${model_name} -brownPath ${brown_path}
                if ! ${dry_run}; then
                    time java -cp target/experiments-smsnp-1.0-SNAPSHOT.jar com.statnlp.experiment.smsnp.CRFPPMain \
                        ${train_path} \
                        -testPath ${base_test_path}.${test_file} \
                        -brownPath ${brown_path} \
                        -tokenizer ${tokenizer} \
                        ${use_gold} \
                        -C 4.0 \
                        ${template} \
                        -logPath ${experiment_dir}/${experiment_name}.log \
                        -modelPath ${experiment_dir}/${model_name}.model \
                        -resultPath ${experiment_dir}/${experiment_name}.result \
                        -writeModelText \
                        -numExamplesPrinted 0 \
                        2>&1 | tee ${experiment_dir}/${experiment_name}.runlog
                fi
            done
        done
    done
done
