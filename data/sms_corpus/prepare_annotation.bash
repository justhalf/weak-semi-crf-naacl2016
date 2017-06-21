for folder in */; do
    for file in $(ls -1 $folder); do
        filename=$folder/${file%%.*}.ann;
        touch $filename;
    done;
    touch $folder/.stats_cache
done
sudo chgrp -R www-data *_*-*/;
sudo chmod 755 *_*-*/;
