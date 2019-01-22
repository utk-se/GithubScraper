import os
import re
from glob import glob

import javalang


def removeComments(string):
    string = re.sub(re.compile("/\*.*?\*/",re.DOTALL ) ,"" ,string) # remove all occurance streamed comments (/*COMMENT */) from string
    string = re.sub(re.compile("//.*?\n" ) ,"" ,string) # remove all occurance singleline comments (//COMMENT\n ) from string
    return string

temp_subfiles = []

pattern = "*.java"

for dir, _, _ in os.walk("/home/manny/PycharmProjects/GithubScraper/java_code_files"):
    temp_subfiles.extend(glob(os.path.join(dir, pattern)))

# print(temp_subfiles)

for file in temp_subfiles:
    with open(file, 'r') as myfile:
        data = myfile.read() #.replace('\n', '')
        data = removeComments(data)
        tree = javalang.parse.parse(data)
        for codeblock in tree.children:
            print(codeblock)
            print("===============")
            for proto in codeblock:
                print(proto)
                print("*************")

        # tokens = list(javalang.tokenizer.tokenize(data))
        # for token in tokens:
        #     print(token.value)
        #     print(token.position)
        #     print(type(token))
