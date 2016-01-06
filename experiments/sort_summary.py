# -*- coding: utf-8 -*-
"""
Here goes the program description
"""

# Import statements
from pprint import pprint

def sort_key(x):
    return (x.split('.')[2], float(x.split('=')[3].strip('%')))

def main():
    with open('full_dataset_summary', 'r') as infile:
        full_summary = infile.read().strip().split('\n\n')
    sorted_summary = sorted(full_summary, key=sort_key)
    with open('full_dataset_summary_sorted', 'w') as outfile:
        for summary in sorted_summary:
            outfile.write(summary+'\n')

if __name__ == '__main__':
    main()

