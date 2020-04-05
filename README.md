# Py-Table task

This java program allows users to select *.csv file, python interpreter (if not found in PATH) and displays the file as an interactive table of editable strings with an ability to sort/unsort columns.
Separators in CSV file are recognized automatically, so you can use either ```;``` or ```,```. Multiline entries are supported. Files are read in chunks, so CSV structure must be consistent. ```pandas``` package for python is used and, if not found, the program might install it automatically.

## Compilation

```
javac src/TableFrame.java
```

## Usage
```
java -cp src TableFrame
```
