# -*- coding: utf-8 -*-
"""
Fix annotations for 1000618
"""

# Import statements
import re

def main():
    with open('sms_corpus.txt', 'r') as infile:
        text = infile.read()
    with open('sms_corpus.ann_', 'r') as infile:
        anns = [ann.strip('\n') for ann in infile.readlines()]
    the_list = set([
            (2978, 2980),
            (4538, 4546),
            (4547, 4555),
            (4556, 4561),
            (4801, 4808),
            (4809, 4816),
            (5383, 5418),
            (5432, 5438),
            (5439, 5445),
            (8232, 8240),
            (13636, 13639),
            (13695, 13696),
            (19773, 19781),
            ])
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
            if (start, end) in the_list:
                the_list.remove((start, end))
                continue
            outfile.write('{}\t{} {} {}\t{}\n'.format(ident, np, start, end, text[start:end]))


if __name__ == '__main__':
    main()

