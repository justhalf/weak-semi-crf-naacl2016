# To run the experiments on full dataset, using the tokenized gold method (to check the upperbound of word-based method on character-level evaluation)
dry_run=true  # Set this to true to just print the experiments that are going to be executed
retest_existing=true # If the model with the same name is present, no retesting is done, unless this value is true

experiment_dir="experiments/full_dataset" # The directory to store all the results

base_test_path="data/SMSNP"  # The base filename for test files. This will be appended with ".dev" or ".test"

if $dry_run; then
    echo "Running in dry run mode"
fi

# Use the gold standard in tokenized form as the answer
# This will test the upperbound of word-based answer in character-level evaluation
if ${do_tokenized_gold}; then
    for test_file in "train" "dev" "test"; do
        for tokenizer in "regex"; do # "whitespace"; do
            experiment_name=tokenized_gold.${tokenizer}.${test_file}
            if [ -a "${experiment_dir}/${experiment_name}.log" ]; then
                if ! ${retest_existing}; then
                    continue
                fi
            fi
            echo ${experiment_name}
            if ! ${dry_run}; then
                time java -jar target/experiments-smsnp-1.0-SNAPSHOT.jar \
                    -algo TOKENIZED_GOLD \
                    -testPath ${base_test_path}.${test_file} \
                    -logPath ${experiment_dir}/${experiment_name}.log
            fi
        done
    done
fi
