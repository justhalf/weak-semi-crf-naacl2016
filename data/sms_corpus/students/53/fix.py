# -*- coding: utf-8 -*-
"""
Fix annotations for 1000660
"""

# Import statements
import re

def main():
    with open('sms_corpus.txt', 'r') as infile:
        text = infile.read().replace('\n', '\r\n')
        # text = infile.read()
    with open('sms_corpus.ann_', 'r') as infile:
        anns = [ann.strip('\n') for ann in infile.readlines()]
    the_list = [
            (101,0,-1),
            (102,0,1),
            (1014,0,-2),
            (1038,-2,2),
            (1046,0,-1),
            (1065,-1,1),
            (1106,3),
            (1405,0,-1),
            (1406,0,1),
            (1421,-1),
            (1446,1),
            (4224,0,-1),
            (4225,0,1),
            (27412,-1),
            (27500,1),
            (29141,-1,1),
            (29142,1,-1),
            (31481,-1),
            (31520,1),
            (34937,0,-1),
            (34938,0,1),
            (34988,-1),
            (34999,1),
            (35010,0,-1),
            (35011,0,1),
            (37377,-1),
            (37424,1),
            (38736,-1),
            (38798,1),
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
            # newlines = len(text[:end]) - len(text[:end].replace('\n', ''))
            # start -= newlines
            # end -= newlines
            # start_idx -= newlines
            # end_idx -= newlines
            outfile.write('{}\t{} {} {}\t{}\n'.format(ident, np, start, end, text[start_idx:end_idx]))


if __name__ == '__main__':
    main()

