#!/usr/bin/python3
# -*- coding: utf-8 -*-
"""
Print statistics of the SMS corpus from the JSON file
"""

# Import statements
from six import print_
import json
from pprint import pprint
from collections import defaultdict
import re
import os
import random
random.seed(63)

def random_bucket(buckets):
    bucket_idx = random.randint(0, len(buckets)-1)
    bucket = buckets[bucket_idx]
    tmp = buckets[:bucket_idx]
    tmp.extend(buckets[bucket_idx+1:])
    buckets[:] = tmp
    return bucket

def main():
    with open('smsCorpus.json') as infile:
        messages = json.load(infile)
    pprint(messages[30000])
    native_count = {'yes':0, 'no':0, 'unknown':0}
    smartphone_count = {'yes':0, 'no':0, 'unknown':0}
    input_method_count = defaultdict(int)
    language_count = defaultdict(int)
    collection_method_count = defaultdict(int)
    selected = []
    texts = set()
    for message in messages:
        message_id = int(message['id'])
        text = message['text']
        text = re.sub('[ \t\r\n]+', ' ', text)
        message['text'] = text
        
        collection_method = message['collectionMethod']['method']
        collection_method_count[collection_method] += 1

        sender = message['sender']

        native = sender['nativeSpeaker'].lower()
        native_count[native] += 1

        smartphone = sender['smartphone'].lower()
        smartphone_count[smartphone] += 1

        input_method = sender['inputMethod']
        input_method_count[input_method] += 1

        language = message['language']
        language_count[language] += 1

        if (
                # collection_method != 'Web-based Transcription' and
                native == 'yes' and 
                # smartphone == 'yes' and
                # input_method in ['Full Keyboard', 'Predictive', 'Multi-tap', 'Swype'] and 
                ' ' in text and
                # len(text)>=7 and
                text not in texts and
                (972<=message_id<=1016 or 4017<=message_id)
                ):
            selected.append(message)
        texts.add(text)
    print_('Collection method:')
    pprint(collection_method_count, width=80)
    print_()
    print_('Is native:')
    pprint(native_count, width=80)
    print_()
    print_('Is smartphone:')
    pprint(smartphone_count, width=80)
    print_()
    print_('Input methods:')
    pprint(input_method_count, width=80)
    print_()
    print_('Language:')
    pprint(dict(language_count), width=80)
    print_()

    print_('Selected ('
           # 'collection_method!="Web-based Transcription", '
           'native="yes", '
           # 'smartphone="yes", '
           # 'inputMethod={"Full Keyboard", "Predictive", "Multi-tap", "Swype"}, '
           'text contains at least one space, '
           # 'text contains at least 7 chars, '
           'no duplicates, '
           '972<=id<=1017 or 4475<=id'
           ')')
    print_('Size of selected: {}'.format(len(selected)))
    # pprint(selected[::len(selected)//10])  # To see some samples

    selected = sorted(selected, key=lambda x: int(x['id']))

    # Write the selected messages into a file
    with open('smsCorpus.txt_', 'w') as outfile:
        for message in selected:
            outfile.write('{} {}\n'.format(message['id'], message['text'].encode('utf-8')))

    # Group messages into group of 100 each
    buckets = []
    for start_idx in range(0, len(selected), 100):
        bucket = selected[start_idx:start_idx+100]
        min_id = int(bucket[0]['id'])
        max_id = int(bucket[-1]['id'])
        buckets.append(bucket)
    
    print_()
    print_('Assigning buckets to students...')
    # Get student IDs
    student_ids = []
    with open('student_ids.txt_', 'r') as infile:
        for line in infile.readlines():
            line = line.strip()
            student_ids.append(int(line))
    # There are 64 students
    
    # Common splitting method for the first 20*3 students
    final_buckets = []
    overlap_bucket_per_student = []
    for i in range(20):
        students = list(student_ids.pop() for i in range(3))
        students_buckets = [[], [], []]
        overlap_bucket = random_bucket(buckets)
        for i in range(3):
            students_buckets[i].append(overlap_bucket)
            overlap_bucket_per_student.append([overlap_bucket])
            for j in range(4):
                students_buckets[i].append(random_bucket(buckets))
        final_buckets.extend(zip(students, students_buckets))

    # Special splitting for the last 4 students
    students = student_ids[:]
    students_buckets = [[], [], [], []]
    overlap_bucket = random_bucket(buckets)
    for i in range(3):
        students_buckets[i].append(overlap_bucket)
        overlap_bucket_per_student.append([overlap_bucket])
    for i in range(4):
        students_buckets[0].append(random_bucket(buckets))
    for i in range(3):
        students_buckets[1].append(random_bucket(buckets))
        students_buckets[2].append(random_bucket(buckets))
    overlap_bucket = random_bucket(buckets)
    students_buckets[1].append(overlap_bucket)
    students_buckets[2].append(overlap_bucket)
    students_buckets[3].append(overlap_bucket)

    overlap_bucket_per_student[-2].append(overlap_bucket)
    overlap_bucket_per_student[-1].append(overlap_bucket)
    overlap_bucket_per_student.append([overlap_bucket])
    for i in range(4):
        students_buckets[3].append(random_bucket(buckets))
    final_buckets.extend(zip(students, students_buckets))
    unused = buckets.pop()
    print_('Confirm len(buckets)==0: {}'.format(len(buckets)))

    # Write the data into separate folders
    student_bucket_file = open('student_bucket_file.txt_', 'w')
    for (student, buckets), overlap_bucket in zip(final_buckets, overlap_bucket_per_student):
        buckets = sorted(buckets, key=lambda x: int(x[0]['id']))
        buckets_id = ['{}-{}'.format(bucket[0]['id'], bucket[-1]['id']) for bucket in buckets]
        overlap_bucket = ' '.join(['{}-{}'.format(bucket[0]['id'], bucket[-1]['id']) for bucket in sorted(overlap_bucket, key=lambda x: int(x[0]['id']))])
        student_bucket_file.write('{0}: {2} {3} {4} {5} {6} | {1}\n'.format(student, overlap_bucket, *buckets_id))
        # try:
        #     os.mkdir('students/{}'.format(student))
        # except:
        #     pass
        # with open('students/{}/sms_corpus.txt'.format(student), 'w') as outfile:
        #     for bucket in buckets:
        #         for message in bucket:
        #             outfile.write('{} {}\n'.format(message['id'], message['text']))
        # with open('students/{}/sms_corpus.ann'.format(student), 'w') as outfile:
        #     pass
    student_bucket_file.close()
    print_('Unused bucket: {}-{}'.format(unused[0]['id'], unused[-1]['id']))
    # with open('students/example/sms_corpus.txt', 'w') as outfile:
    #     for message in unused:
    #         outfile.write('{} {}\n'.format(message['id'], message['text']))
    # with open('students/example/sms_corpus.ann', 'w') as outfile:
    #     pass

if __name__ == '__main__':
    main()

