# -*- coding: utf-8 -*-
"""
Fix annotations for 1000488
To be run after running these:
    - Run fix.py
    - Copy fixed.ann into sms_corpus.ann2_
"""

# Import statements
import re

def main():
    with open('sms_corpus.txt', 'r') as infile:
        text = infile.read()
    with open('sms_corpus.ann2_', 'r') as infile:
        anns = [ann.strip('\n') for ann in infile.readlines()]
    the_list = set([
            (20017, 20019),
            (20539, 20542),
            (20612, 20613),
            (21007, 21018),
            (21201, 21216),
            (21412, 21423),
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

