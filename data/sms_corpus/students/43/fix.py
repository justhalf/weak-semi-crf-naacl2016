# -*- coding: utf-8 -*-
"""
Fix annotations for 1000591
"""

# Import statements

def main():
    with open('sms_corpus.txt', 'r') as infile:
        text = infile.read()
    with open('sms_corpus.ann_', 'r') as infile:
        anns = [ann.strip('\n') for ann in infile.readlines()]
    the_list = [
            (14066,-7),
            (14048,-8),
            (13257,-7),
            (13150,-6),
            (13117,-5),
            (13018,-4),
            (12952,-5),
            (12901,-4),
            (12839,-3),
            (12201,-2),
            (12024,-1)
            ]
    with open('fixed.ann', 'w') as outfile:
        for ann in anns:
            ident, np_start_end, string = ann.split('\t')
            np_start_end = np_start_end.split(' ')
            np = np_start_end[0]
            start = np_start_end[1]
            end = np_start_end[-1]
            start = int(start)
            end = int(end)
            start_idx = start
            end_idx = end
            if start > 113982:
                start += 1
                end += 1
                start_idx += 1
                end_idx += 1
            else:
                for idx, dec in the_list:
                    if start >= idx:
                        start += dec
                        end += dec
                        start_idx += dec
                        end_idx += dec
                        break
            outfile.write('{}\t{} {} {}\t{}\n'.format(ident, np, start, end, text[start_idx:end_idx]))


if __name__ == '__main__':
    main()

