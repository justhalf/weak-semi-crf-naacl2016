# -*- coding: utf-8 -*-
"""
To separate the file smsCorpus_en_2015.03.09_all.txt into buckets of 1000 SMS
"""

# Import statements
from six import print_
import os

def main():
    with open('all/smsCorpus_en_2015.03.09_all.txt', 'r') as infile:
        first_id_file = 0
        first_date_file = '0_0'
        first_id_folder = 0
        first_date_folder = '0_0'
        prev_id = None
        prev_date = None
        folder_name = None
        file_name = None
        count = 0
        messages = None
        for line in infile.readlines():
            try:
                message_id, message_date, message_txt = line.strip().split(' ', 2)
            except Exception as e:
                print_(line)
                raise e
            message_date = message_date.replace('/', '_')
            if count % 100 == 0:
                if messages is not None:
                    file_name = '{}-{}__{}-{}.txt'.format(first_id_file, prev_id, first_date_file, prev_date)
                    with open('{}/{}'.format(folder_name, file_name), 'w') as outfile:
                        for message in messages:
                            outfile.write('{} {} {}\n'.format(*message))
                first_id_file = message_id
                first_date_file = message_date
                messages = []
            if count % 1000 == 0:
                if folder_name is not None:
                    os.rename(folder_name, '{}-{}__{}-{}'.format(first_id_folder, prev_id, first_date_folder, prev_date))
                first_id_folder = message_id
                first_date_folder = message_date
                folder_name = '{}-{}__{}-{}'.format(message_id, 'None', message_date, 'None')
                os.mkdir(folder_name)
            messages.append((message_id, message_date, message_txt))
            count += 1
            prev_id = message_id
            prev_date = message_date
        if messages is not None:
            file_name = '{}-{}__{}-{}.txt'.format(first_id_file, prev_id, first_date_file, prev_date)
            with open('{}/{}'.format(folder_name, file_name), 'w') as outfile:
                for message in messages:
                    outfile.write('{} {} {}\n'.format(*message))
        os.rename(folder_name, '{}-{}__{}-{}'.format(first_id_folder, prev_id, first_date_folder, prev_date))

if __name__ == '__main__':
    main()

