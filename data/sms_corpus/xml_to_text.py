# -*- coding: utf-8 -*-
"""
To convert the SMS corpus from XML into txt format
"""

# Import statements
from six import print_
from bs4 import BeautifulSoup as BS
from pprint import pprint
import json
import sys
import pickle
import os
import re

def main():
    print_('Loading from xml file')
    with open('../smsCorpus_en_2015.03.09_all.xml', 'r') as infile:
    # with open('../smsCorpus_en_2015.03.09_all_small.xml', 'r') as infile:
        soup = BS(infile.read(), 'lxml')
    messages = []
    for message_tag in soup.find_all('message'):
        message = {}
        message['id'] = message_tag['id']
        message['text'] = message_tag.find('text').get_text()
        # message['text'] = re.sub('[ \t\n]+', ' ', message_tag.find('text').get_text())
        phone_model = message_tag.find('phonemodel')
        sender = {}
        sender['srcNumber'] = message_tag.find('srcnumber').get_text()
        sender['manufacturer'] = phone_model['manufactuer']
        sender['smartphone'] = phone_model['smartphone']
        sender['userID'] = message_tag.find('userid').get_text()
        sender['age'] = message_tag.find('age').get_text()
        sender['gender'] = message_tag.find('gender').get_text()
        sender['nativeSpeaker'] = message_tag.find('nativespeaker').get_text()
        sender['country'] = message_tag.find('country').get_text()
        sender['city'] = message_tag.find('city').get_text()
        sender['experience'] = message_tag.find('experience').get_text()
        sender['frequency'] = message_tag.find('frequency').get_text()
        sender['inputMethod'] = message_tag.find('inputmethod').get_text()
        message['sender'] = sender
        destination = {}
        destination['destNumber'] = message_tag.find('destnumber').get_text()
        destination['country'] = message_tag.find('destination')['country']
        message['destination'] = destination
        profile = message_tag.find('messageprofile')
        message['language'] = profile['language']
        message['time'] = profile['time']
        message['type'] = profile['type']
        collection_method = message_tag.find('collectionmethod')
        collector = {}
        collector['collector'] = collection_method['collector']
        collector['method'] = collection_method['method']
        collector['time'] = collection_method['time']
        message['collectionMethod'] = collector
        messages.append(message)
    with open('smsCorpus.json', 'w') as outfile:
        json.dump(messages, outfile, sort_keys=True)
    with open('../smsCorpus_en_2015.03.09_all.txt', 'w') as outfile:
        for message in messages:
            outfile.write('{} {} {}\n'.format(message['id'], message['collectionMethod']['time'], message['text']))

if __name__ == '__main__':
    main()

