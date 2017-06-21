# Prints number of tokens regarding the tokenized data (the SMSNP.conll.* files)
color=`tput bold setaf 7 2> /dev/null`
reset=`tput sgr0 2> /dev/null`
echo "Number of tokens"
for tokenizer in "regex" "whitespace"; do
    for gold in "" ".gold"; do
        sum=0
        for dataset in "train" "dev" "test"; do
            filename="SMSNP.conll.$tokenizer$gold.$dataset"
            lines=$(grep -v '^$' $filename | wc -l )
            sum=$((sum+lines))
            echo ${color}$lines${reset} $filename
        done
        echo "Total for SMSNP.conll.$tokenizer$gold.*: ${color}$sum${reset}"
    done
done
