#!/usr/bin/python2
# coding=utf-8

##===================================================================##
#   Turn the annotated SMS data into label format
#   Jie Yang
#   Nov. 4, 2015
#   
#   EDIT by Aldrian Obaja
#   Change to output spans instead of tokenized version
#   Note that this script requires Python 2, it does not run in Python 3
# 
##===================================================================##
import sys
import os
import types
import re
import random

def get_sentence_list(txt_list, use_windows_endline=False):
    sent_end = 1
    sent_list = []
    for sentence in txt_list:
        sent_pair = []
        sent_pair.append(sent_end)
        sent_pair.append(sentence.decode('utf-8'))
        sent_list.append(sent_pair)
        sent_end += len(sentence.decode('utf-8')) 
        if use_windows_endline:
            sent_end += 1
    return sent_list    

def list_clean(input_list):
    output_list = []
    sent = ''
    if len(input_list) < 1:
        print 'error'
        return 0
    else:
        output_list.append(input_list[0])
        for idx in range(1, len(input_list)):
            if idx != len(input_list) -1:
                sent += input_list[idx].encode('utf-8')
            else:
                sent += input_list[idx].encode('utf-8')
    return sent

def get_sentence_position(input_list):
    position_list = []
    for sentence, spans in input_list:
        if len(sentence) < 2:
            continue
        words = sentence.split(' ')
        if  words[0].find('#[') >= 0:
            continue
        position_list.append(int(words[0]))
    return set(position_list)

def sort_ann(ann_list):
    result = []
    for ann_label in ann_list:
        line_ann_list = ann_label.split('\t')
        if len(line_ann_list) != 3:
            print 'ann file error type 1! {}'.format(ann_label)
            continue
        np_pair = line_ann_list[1].split(' ')
        if len(np_pair) < 3:
            print 'ann file error type 2! {}'.format(line_ann_list[1])
            continue
        np_pair[1] = int(np_pair[1])
        np_pair[2] = int(np_pair[2])
        result.append((np_pair[1], np_pair[2], line_ann_list[2]))
    result.sort(key=lambda x: x[0])
    tmp = result[:]
    result = []
    prev_ann = None
    for ann in tmp:
        if prev_ann is not None:
            if prev_ann[1] > ann[0]:
                if prev_ann[1] - prev_ann[0] >= ann[1] - ann[0]:
                    print 'Ignoring {}, overlap with {}'.format(ann, prev_ann)
                else:
                    result[-1] = ann
                    print 'Ignoring {}, overlap with {}'.format(prev_ann, ann)
                    prev_ann = ann
                continue
        result.append(ann)
        prev_ann = ann
    return result

def label_extract(ann_input, txt_input):
    print 'begin extract file: ', ann_input
    ann_list = open(ann_input, 'rU').readlines()
    txt_list = open(txt_input, 'rU').readlines()
    use_windows_endline = False
    if '46' in txt_input:
        txt = open(txt_input, 'rU').read()
        txt = txt[:8195] + txt[8195:].replace('\n', '\r\n')
        txt_list = txt.split('\n')
        txt_list = [text+'\n' for text in txt_list if text != '']
    if '53' in txt_input:
        use_windows_endline = True
    sent_list = get_sentence_list(txt_list, use_windows_endline)
    len_sent_list = len(sent_list)
    new_sent_list = []
    temp_list = []
    last_line_number = 0
    last_line_sent = ''
    last_end = 0
    last_np_pair_begin = 0
    ann_list = sort_ann(ann_list)
    prev_idx = 0
    spans = []
    for ann_label in ann_list:
        np_pair = ('Noun-Phrase', ann_label[0], ann_label[1])

        for idx in range(prev_idx, len_sent_list):
            sent_list[idx][0] = int(sent_list[idx][0])
            # print sent_list[idx]
            if  np_pair[1] <= sent_list[idx][0]:
                if last_line_number != sent_list[idx][0]:  # This annotation is in new sentence, put previous sentence in the annotated list
                    temp_list.append(last_line_sent[last_end:])
                    if temp_list != []:
                        new_sent_list.append((list_clean(temp_list), spans))
                    spans = []
                    temp_list = []
                    temp_list.append(int(sent_list[idx-1][0]))
                    np_start = np_pair[1]-int(sent_list[idx-1][0]) + 1
                    np_end = np_pair[2]-int(sent_list[idx-1][0]) +1
                    spans.append((np_start, np_end, 'NP'))
                    last_line_sent = sent_list[idx-1][1]
                    annotated_text = last_line_sent[np_start:np_end]
                    if annotated_text == '':
                        print 'np_start: {}, np_end: {}, start_idx: {}, sent: {}'.format(np_start, np_end, sent_list[idx][0], last_line_sent)
                    if annotated_text[-1] == ' ':
                        print 'Ends with space: np_pair[1]:{}, np_pair[2]:{}, text:{}, sent:{}'.format(np_pair[1], np_pair[2], annotated_text, last_line_sent)
                    temp_list.append(last_line_sent[0:np_start] + '#[' + last_line_sent[np_start:np_end] + ']#')
                    last_line_number = sent_list[idx][0]
                    last_end = np_end
                    # print temp_list
                    prev_idx = idx
                    break
                else:  # This annotation is in the same sentence as previous one, add to temporary list
                    np_start = np_pair[1]-int(sent_list[idx-1][0]) + 1
                    np_end = np_pair[2]-int(sent_list[idx-1][0]) +1
                    spans.append((np_start, np_end, 'NP'))
                    last_line_sent = sent_list[idx-1][1]
                    temp_list.append(last_line_sent[last_end:np_start] + '#[' + last_line_sent[np_start:np_end] + ']#')
                    annotated_text = last_line_sent[np_start:np_end]
                    if annotated_text == '':
                        print 'np_start: {}, np_end: {}, start_idx: {}, sent: {}'.format(np_start, np_end, sent_list[idx][0], last_line_sent)
                    if annotated_text[-1] == ' ':
                        print 'Ends with space: np_pair[1]:{}, np_pair[2]:{}, text:{}, sent:{}'.format(np_pair[1], np_pair[2], annotated_text, last_line_sent)
                    last_line_number = sent_list[idx][0]
                    last_end = np_end
                    # print temp_list
                    prev_idx = idx
                    break
            else:
                prev_idx = idx
                continue
    labeled_position = get_sentence_position(new_sent_list)

    for full_sentence in sent_list:
        full_position = int(full_sentence[1].split(' ')[0])
        if full_position in labeled_position:
            continue
        else:
            new_sent_list.append((full_sentence[1].encode('utf-8'), []))

    # post process list, delete null elements
    annotated_list = []
    for pairs, spans in new_sent_list:
        if len(pairs) < 2:
            continue
        annotated_list.append((pairs, spans))
    return annotated_list

def sentence_list_combine(old_sentence_list, add_sentence_list):
    old_positions = get_sentence_position(old_sentence_list)
    for sentence, spans in add_sentence_list:
        if  sentence.split(' ')[0].find('#[') >= 0:
            continue
        add_sent_position = int(sentence.split(' ')[0])
        if add_sent_position in old_positions:
            # print add_sent_position
            continue
        else:
            old_sentence_list.append((sentence, spans))
    return old_sentence_list

def sentence_list_to_file(sentence_list, output_file):
    outfile = open(output_file, 'w')
    for sentence, spans in sentence_list:
        space = sentence.find(' ')
        clean_sentence = sentence.replace('#[', '').replace(']#', '')[space+1:].strip('\r\n')
        spans = ['{},{} {}'.format(start-space-1, end-space-1, label) for start, end, label in spans]
        outfile.write('{}\n{}\n'.format(clean_sentence, '|'.join(spans)))
        outfile.write('\n')

    outfile.close()
    return 1

if __name__ == '__main__':
    current_path = os.path.dirname(__file__)
    train_file = 'SMSNP.train'
    dev_file = 'SMSNP.dev'
    test_file = 'SMSNP.test'
    father_path = os.path.join(current_path, 'sms_corpus/students/')
    ann_child_path = 'sms_corpus.ann'
    txt_child_path = 'sms_corpus.txt'
    for root, dirs, files in os.walk(father_path):
        folder_list = dirs
        break
    full_sentence_list = []
    excluded_students = set(['36', # Bad annotation
                             '50', # No annotation
                             '63', # No annotation
                             ])
    for folder in folder_list:
        if folder in excluded_students:
            continue
        input_folder = os.path.join(father_path,folder)
        input_ann_file = os.path.join(input_folder,ann_child_path)
        input_txt_file = os.path.join(input_folder,txt_child_path)
        annotated_list = label_extract(input_ann_file, input_txt_file)
        sentence_list_combine(full_sentence_list, annotated_list)
    random.seed(10)
    random.shuffle(full_sentence_list)

    whole_sentence_number = len(full_sentence_list)

    train_rate = 0.8
    dev_rate = 0.1
    test_rate = 0.1
    
    train_part = int(train_rate/(train_rate+dev_rate + test_rate)* whole_sentence_number)
    dev_part = int(dev_rate/(train_rate+dev_rate + test_rate)* whole_sentence_number)
    test_part = int(test_rate/(train_rate+dev_rate + test_rate)* whole_sentence_number)
    sentence_list_to_file(full_sentence_list[0:train_part], train_file)
    sentence_list_to_file(full_sentence_list[train_part:train_part+dev_part], dev_file)
    sentence_list_to_file(full_sentence_list[train_part+dev_part:train_part+dev_part+test_part], test_file)

    print 'Whole corpus sentence number: ', whole_sentence_number
    print 'Train corpus sentence number: ', train_part
    print 'Dev corpus sentence number: ', dev_part
    print 'Test corpus sentence number: ', test_part
    print 'data extraction finished'

