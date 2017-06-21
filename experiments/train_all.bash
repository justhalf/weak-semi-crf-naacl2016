# Linear CRF

run_name="lcrf-trigram" \
    additional_features="" \
    experiment_dir="models" \
    bash train_word_lcrf.bash

run_name="lcrf-trigram-word_shape" \
    additional_features=",word_shape" \
    experiment_dir="models" \
    bash train_word_lcrf.bash

run_name="lcrf-trigram-brown_cluster" \
    additional_features=",brown_cluster" \
    experiment_dir="models" \
    bash train_word_lcrf.bash

run_name="lcrf-trigram-brown_cluster-word_shape" \
    additional_features=",brown_cluster,word_shape" \
    experiment_dir="models" \
    bash train_word_lcrf.bash

run_name="lcrf-trigram-prefix-suffix" \
    additional_features=",prefix,suffix" \
    experiment_dir="models" \
    bash train_word_lcrf.bash

run_name="lcrf-trigram-prefix-suffix-word_shape" \
    additional_features=",prefix,suffix,word_shape" \
    experiment_dir="models" \
    bash train_word_lcrf.bash

run_name="lcrf-trigram-prefix-suffix-brown_cluster" \
    additional_features=",prefix,suffix,brown_cluster" \
    experiment_dir="models" \
    bash train_word_lcrf.bash

run_name="lcrf-trigram-prefix-suffix-brown_cluster-word_shape" \
    additional_features=",prefix,suffix,brown_cluster,word_shape" \
    experiment_dir="models" \
    bash train_word_lcrf.bash

# Semi-CRF

run_name="semi-crf_seg6_prev_words_next_bigram_segment" \
    algo="WORD_SEMI_CRF" \
    additional_features="" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="semi-crf_seg6_prev_words_next_bigram_segment_shape" \
    algo="WORD_SEMI_CRF" \
    additional_features=",prev_word_shape,word_shapes,next_word_shape" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="semi-crf_seg6_prev_words_next_bigram_segment_cluster" \
    algo="WORD_SEMI_CRF" \
    additional_features=",prev_word_cluster,word_clusters,next_word_cluster" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="semi-crf_seg6_prev_words_next_bigram_segment_cluster_shape" \
    algo="WORD_SEMI_CRF" \
    additional_features=",prev_word_cluster,word_clusters,next_word_cluster,prev_word_shape,word_shapes,next_word_shape" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="semi-crf_seg6_affix_prev_words_next_bigram_segment" \
    algo="WORD_SEMI_CRF" \
    additional_features=",segment_prefix,segment_suffix" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="semi-crf_seg6_affix_prev_words_next_bigram_segment_shape" \
    algo="WORD_SEMI_CRF" \
    additional_features=",segment_prefix,segment_suffix,prev_word_shape,word_shapes,next_word_shape" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="semi-crf_seg6_affix_prev_words_next_bigram_segment_cluster" \
    algo="WORD_SEMI_CRF" \
    additional_features=",segment_prefix,segment_suffix,prev_word_cluster,word_clusters,next_word_cluster" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="semi-crf_seg6_affix_prev_words_next_bigram_segment_cluster_shape" \
    algo="WORD_SEMI_CRF" \
    additional_features=",segment_prefix,segment_suffix,prev_word_cluster,word_clusters,next_word_cluster,prev_word_shape,word_shapes,next_word_shape" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

# Weak Semi-CRF

run_name="weak-semi-crf_seg6_prev_words_next_bigram_segment" \
    algo="WORD_WEAK_SEMI_CRF" \
    additional_features="" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="weak-semi-crf_seg6_prev_words_next_bigram_segment_shape" \
    algo="WORD_WEAK_SEMI_CRF" \
    additional_features=",prev_word_shape,word_shapes,next_word_shape" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="weak-semi-crf_seg6_prev_words_next_bigram_segment_cluster" \
    algo="WORD_WEAK_SEMI_CRF" \
    additional_features=",prev_word_cluster,word_clusters,next_word_cluster" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="weak-semi-crf_seg6_prev_words_next_bigram_segment_cluster_shape" \
    algo="WORD_WEAK_SEMI_CRF" \
    additional_features=",prev_word_cluster,word_clusters,next_word_cluster,prev_word_shape,word_shapes,next_word_shape" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="weak-semi-crf_seg6_affix_prev_words_next_bigram_segment" \
    algo="WORD_WEAK_SEMI_CRF" \
    additional_features=",segment_prefix,segment_suffix" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="weak-semi-crf_seg6_affix_prev_words_next_bigram_segment_shape" \
    algo="WORD_WEAK_SEMI_CRF" \
    additional_features=",segment_prefix,segment_suffix,prev_word_shape,word_shapes,next_word_shape" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="weak-semi-crf_seg6_affix_prev_words_next_bigram_segment_cluster" \
    algo="WORD_WEAK_SEMI_CRF" \
    additional_features=",segment_prefix,segment_suffix,prev_word_cluster,word_clusters,next_word_cluster" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash

run_name="weak-semi-crf_seg6_affix_prev_words_next_bigram_segment_cluster_shape" \
    algo="WORD_WEAK_SEMI_CRF" \
    additional_features=",segment_prefix,segment_suffix,prev_word_cluster,word_clusters,next_word_cluster,prev_word_shape,word_shapes,next_word_shape" \
    experiment_dir="models" \
    bash train_word_semi_crfs.bash
