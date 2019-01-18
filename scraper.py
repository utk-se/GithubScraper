from robobrowser import RoboBrowser
import requests
import json
from bs4 import BeautifulSoup
import random
from pymongo import MongoClient
import datetime
from tqdm import tqdm
import re

HEADERS_LIST = [
    'Mozilla/5.0 (Windows; U; Windows NT 6.1; x64; fr; rv:1.9.2.13) Gecko/20101203 Firebird/3.6.13',
    'Mozilla/5.0 (compatible, MSIE 11, Windows NT 6.3; Trident/7.0; rv:11.0) like Gecko',
    'Mozilla/5.0 (Windows; U; Windows NT 6.1; rv:2.2) Gecko/20110201',
    'Opera/9.80 (X11; Linux i686; Ubuntu/14.10) Presto/2.12.3
browser = RoboBrowser(session=session, user_agent=random.choice(HEADERS_LIST), parser="lxml")
page = 1
url = "https://github.com/search?l=Java&o=desc&p=" + str(page) + "&q=java&s=stars&type=Repositories"
browser.open(url)
while len(link) < 100:
    browser.open(url)
88 Version/12.16',
    'Mozilla/5.0 (Windows NT 5.2; RW; rv:7.0a1) Gecko/20091211 SeaMonkey/9.23a1pre'
]

link = []

session = requests.Session()
browser = RoboBrowser(session=session, user_agent=random.choice(HEADERS_LIST), parser="lxml")
page = 1
url = "https://github.com/search?l=Java&o=desc&p=" + str(page) + "&q=java&s=stars&type=Repositories"
browser.open(url)
while len(link) < 100:
    browser.open(url)
    results = browser.find_all("a", {"class": "v-align-middle"})
    print('results', len(results))
    page = page + 1
    url = "https://github.com/search?l=Java&o=desc&p=" + str(page) + "&q=java&s=stars&type=Repositories"
    for items in results:
        link.append(items)
    print('link', len(link))
    print('page', page)

print(link)