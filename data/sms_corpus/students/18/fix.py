# -*- coding: utf-8 -*-
"""
Fix annotations for 1000451
"""

# Import statements

def main():
    with open('sms_corpus.txt') as infile:
        text = infile.read()
    with open('sms_corpus.ann_') as infile:
        anns = [ann.strip() for ann in infile.readlines()]
    with open('fixed.ann', 'w') as outfile:
        for ann in anns:
            ident, np_start_end, string = ann.split('\t')
            np, start, end = np_start_end.split(' ')
            start = int(start)
            end = int(end)
            start_idx = start
            end_idx = end
            if end == 2857 or end == 26260:
                end -= 1
                end_idx -= 1
            # if start == 20304:
            #     start += 1
            #     end += 1
            #     start_idx += 1
            #     end_idx += 1
            # elif start > 20304:
            #     start += 2
            #     start_idx += 2
            #     end += 2
            #     end_idx += 2
            # if start >= 21195:
            #     start_idx += 2
            #     end_idx += 2
            outfile.write('{}\t{} {} {}\t{}\n'.format(ident, np, start, end, text[start_idx:end_idx]))


if __name__ == '__main__':
    main()

