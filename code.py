import pandas
import sys

if len(sys.argv) < 2:
    print("Usage: " + sys.argv[0] + " <filename.csv> [<separator>]")
    exit()

data = pandas.read_csv(sys.argv[1], engine='python', sep=None, keep_default_na=False)
print(data.shape[0])
print(data.shape[1])
input()
for i in range(data.shape[1]):
    print(data.columns[i])
    input()
for i in range(data.shape[0]):
    for j in range(data.shape[1]):
        print(data.iloc[i][j])
        input()
