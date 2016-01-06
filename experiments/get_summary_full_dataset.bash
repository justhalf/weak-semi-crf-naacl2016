for file in $(ls -1 full_dataset | grep .log); do
    echo "${file}";
    tail -3 "full_dataset/${file}" | head -1
    echo
done
