import pandas
import sys

if len(sys.argv) < 2:
    print("Usage: " + sys.argv[0] + " <filename.csv> [<separator>]")
    exit()

is_first = True
chunks = pandas.read_csv(sys.argv[1], engine='python', sep=None, keep_default_na=False, chunksize=1000000)
for chunk in chunks:
    if (is_first):
        # First chunk - print width and column names
        input()
        print(chunk.shape[1])
        for i in range(chunk.shape[1]):
            input()
            print(chunk.columns[i])
        is_first = False
    # Every chunk - print height and cells
    input()
    print(chunk.shape[0])
    for i in range(chunk.shape[0]):
        for j in range(chunk.shape[1]):
            input()
            print(chunk.iloc[i][j])
input()
print(-1)