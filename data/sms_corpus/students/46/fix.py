# -*- coding: utf-8 -*-
"""
Fix annotations for 1000601
"""

# Import statements
import re

def main():
    with open('sms_corpus.txt', 'r') as infile:
        text = infile.read()
    text = text[:8195] + text[8195:].replace('\n', '\r\n')
    with open('sms_corpus.ann_', 'r') as infile:
        anns = [ann.strip('\n') for ann in infile.readlines()]
    the_list = [
            (7757,-1),
            (7770,1),
            (8195,-110),
            (8185,-1),
            (11129,-1),
            (11151,1),
            (27189,0,-1),
            (27190,0,1),
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
            for adj in the_list:
                idx = adj[0]
                dec = adj[1]
                if len(adj) > 2:
                    end_dec = adj[2]
                else:
                    end_dec = 0
                if start >= idx:
                    start += dec
                    end += dec
                    start_idx += dec
                    end_idx += dec
                    end += end_dec
                    end_idx += end_dec
            outfile.write('{}\t{} {} {}\t{}\n'.format(ident, np, start, end, text[start_idx:end_idx]))


if __name__ == '__main__':
    main()

