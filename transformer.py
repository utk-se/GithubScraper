import os
import re
from glob import glob
from tqdm import tqdm
import javalang
import pandas as pd


def get_source_code(commitId, project):
    import random
    import requests
    from robobrowser import RoboBrowser

    HEADERS_LIST = [
        'Mozilla/5.0 (Windows; U; Windows NT 6.1; x64; fr; rv:1.9.2.13) Gecko/20101203 Firebird/3.6.13',
        'Mozilla/5.0 (compatible, MSIE 11, Windows NT 6.3; Trident/7.0; rv:11.0) like Gecko',
        'Mozilla/5.0 (Windows; U; Windows NT 6.1; rv:2.2) Gecko/20110201',
        'Opera/9.80 (X11; Linux i686; Ubuntu/14.10) Presto/2.12.388 Version/12.16',
        'Mozilla/5.0 (Windows NT 5.2; RW; rv:7.0a1) Gecko/20091211 SeaMonkey/9.23a1pre'
    ]

    link = []

    session = requests.Session()
    browser = RoboBrowser(session=session, user_agent=random.choice(HEADERS_LIST), parser="lxml")
    url = "https://github.com/" + project.replace("-", "/") + "/commit/" + commitId

    browser.open(url + "?diff=unified")
    results = browser.find_all("a")
    for item in results:
        if ".java" in str(item):
            second_url = "https://raw.githubusercontent.com/" + project.replace("-",
                                                                                "/") + "/" + commitId + "/" + item.string
            browser.open(second_url)
            return browser.find().text


def removeComments(string):
    string = re.sub(re.compile("/\*.*?\*/", re.DOTALL), "",
                    string)  # remove all occurance streamed comments (/*COMMENT */) from string
    string = re.sub(re.compile("//.*?\n"), "",
                    string)  # remove all occurance singleline comments (//COMMENT\n ) from string
    return string


temp_subfiles = []

pattern = "*.java"

for dir, _, _ in os.walk("/home/manny/PycharmProjects/GithubScraper/java_code_files"):
    temp_subfiles.extend(glob(os.path.join(dir, pattern)))

# print(temp_subfiles)

if (False):
    for file in tqdm(temp_subfiles):
        with open(file, 'r') as myfile:
            data = myfile.read()  # .replace('\n', '')
            # data = removeComments(data)
            tree = javalang.parse.parse(data)
            # for codeblock in tree.children:
            #     print(codeblock)
            #     print("===============")
            #     for proto in codeblock:
            #         print(proto)
            #
            #         print("*************")
            #
            # tokens = list(javalang.tokenizer.tokenize(data))
            # for token in tokens:
            #     # print(token.value)
            #     # print(token.position)
            #     print(type(token))
df = pd.read_csv("1151-commits-labeled-with-maintenance-activities.csv", sep="#")

code = {"code": [], "label": [], "repoSource": [], 'add': [], 'allow': [], 'bug': [], 'chang': [], 'error': [],
        'fail': [], 'fix': [], 'implement': [],
        'improv': [], 'issu': [], 'method': [], 'new': [], 'npe': [], 'refactor': [], 'remov': [], 'report': [],
        'set': [], 'support': [], 'test': [], 'use': []}

print(df.columns)

for index, row in tqdm(df.iterrows()):
    try:
        tree = javalang.parse.parse(get_source_code(row['commitId'], row['project']))
        for codeblock in tree.children:
            print(codeblock)
            code['code'].append(codeblock)
            code['label'].append(row['label'])
            code['repoSource'].append(row['project'])
            code['add'].append(row['add'])
            code['allow'].append(row['allow'])
            code['bug'].append(row['bug'])
            code['chang'].append(row['chang'])
            code['error'].append(row['error'])
            code['fail'].append(row['fail'])
            code['fix'].append(row['fix'])
            code['implement'].append(row['implement'])
            code['improv'].append(row['improv'])
            code['issu'].append(row['issu'])
            code['method'].append(row['method'])
            code['new'].append(row['new'])
            code['npe'].append(row['npe'])
            code['refactor'].append(row['refactor'])
            code['remov'].append(row['remov'])
            code['report'].append(row['report'])
            code['set'].append(row['set'])
            code['support'].append(row['support'])
            code['test'].append(row['test'])
            code['use'].append(row['use'])
    except:
        pass
