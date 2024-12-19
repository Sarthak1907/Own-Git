# Git

In this challenge, you'll build a small Git implementation that's capable of
initializing a repository, creating commits and cloning a public repository.
Along the way we'll learn about the `.git` directory, Git objects (blobs,
commits, trees etc.), Git's transfer protocols and more.

## Project Details

### Passing the first stage

The entry point for your Git implementation is in `src/main/java/Main.java`.
Study and uncomment the relevant code, and push your changes to pass the first
stage:

```sh
git add .
git commit -m "pass 1st stage" # any msg
git push origin master
```

### Stage 2 -- Initialize the .git directory

#### git init command

Check if .git directory exists.

Check if .git/objects directory exists.

Check if .git/refs directory exists.

Check if .git/HEAD file exists.

Check if .git/HEAD contains either "ref: refs/heads/main\n" or "ref: refs/heads/master\n".

### Stage 3 -- Read a blob object

#### git cat-file command

The tester will verify that the output of our program matches the binary data that the blob contains.

### Stage 4 -- Create a blob object

#### git hash-object command

The tester will verify that:

Our program prints a 40-character SHA hash to stdout.

The file written to .git/objects matches what the official git implementation would write.

### Stage 5 -- Read a tree object

#### git ls-tree command

It'll verify that the output of your program matches the contents of the tree object.

In a tree object file, the SHA hashes are not in hexadecimal format. They're just raw bytes (20 bytes long).

In a tree object file, entries are sorted by their name. The output of ls-tree matches this order.

### Stage 6 -- Write a tree object

#### git write-tree command

Expected to write the entire working directory as a tree object and print the 40-char SHA to stdout.

The tester will verify that the output of your program matches the SHA hash of the tree object that the official git implementation would write.

The implementation of git write-tree here differs slightly from the official git implementation. The official git implementation uses the staging area to determine what to write to the tree object. We'll just assume that all files in the working directory are staged.

### Stage 7 -- Create a commit

#### git commit-tree command

Our program must create a commit object and print its 40-char SHA to stdout.

To keep things simple:

We'll receive exactly one parent commit.

We'll receive exactly one line in the message.

We're free to hardcode any valid name/email for the author/committer fields.

To verify your changes, the tester will read the commit object from the .git directory. It'll use the git show command to do this.

### Stage 8 -- Clone a repository

Our program must create "some_dir" and clone the given repository into it.

To verify your changes, the tester will:

Check the contents of a random file
Read commit object attributes from the .git directory

## Author

- [@Sarthak1907](https://github.com/Sarthak1907)

## Reference

Code Crafters -- (https://app.codecrafters.io/catalog)

### How to RUN code

#### Prerequisites:

1. Java Development Kit (JDK): Ensure you have a JDK installed and configured.
2. Visual Studio Code: Download and install the latest version.
3. Extensions: Install the following extensions: Java Extension Pack

#### Steps to Run the Application:

1. Clone the Repository.
2. Open the Project in VS Code.
3. Build the Project
4. Using the Terminal run the Code.
