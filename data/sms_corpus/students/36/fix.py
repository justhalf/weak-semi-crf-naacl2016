# -*- coding: utf-8 -*-
"""
Fix annotations for 1000548
"""

# Import statements
import re

def main():
    with open('sms_corpus.txt', 'r') as infile:
        text = infile.read()
    with open('sms_corpus.ann_', 'r') as infile:
        anns = [ann.strip('\n') for ann in infile.readlines()]
    the_list = [
            ]
    with open('fixed.ann', 'w') as outfile:
        for ann in anns:
            ann = re.sub(r'\t[ \t\n]+$', r'\t ', ann)
            ident, np_start_end, string = ann.split('\t')
            np_start_end = np_start_end.split(' ')
            np = np_start_end[0]
            start = np_start_end[1]
            end = np_start_end[-1]
            start = int(start)
            end = int(end)
            start_idx = start
            end_idx = end
            annotated_text = text[start_idx:end_idx]
            if annotated_text[-1] == ' ':
                start -= 1
                end -= 1
                start_idx -= 1
                end_idx -= 1
            if annotated_text[-1] == '.':
                end -= 1
                end_idx -= 1
            if start == 10533:
                start = 10534
                start_idx = 10534
            # annotated_text = text[start_idx:end_idx]
            # if annotated_text[0] == ' ':
            #     start += 1
            #     start_idx += 1
            outfile.write('{}\t{} {} {}\t{}\n'.format(ident, np, start, end, text[start_idx:end_idx]))


if __name__ == '__main__':
    main()

