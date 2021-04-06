import json
import os

class bcolors:
    OKGREEN = '\033[92m'
    FAIL = '\033[91m'
    WARNING = '\033[93m'
    ENDC = '\033[0m'


def print_failed(message: str):
    print(bcolors.FAIL + "Failed: [" + message + "]" + bcolors.ENDC)


def print_passed(message: str):
    print(bcolors.OKGREEN + "Passed: [" + message + "]" + bcolors.ENDC)


def print_warning(message: str):
    print(bcolors.WARNING + "Warning: [" + message + "]" + bcolors.ENDC)


#   delete_DS_Store_files
#   
#   Before executing all test delete all .DS_Store files in directories. 
#   These files are automatically created by macOS and can be safely removed. 
#   
def delete_DS_Store_files():
    print("Deleting .DS_Store files")
    deleted_files = 0

    for subdir, _, files in os.walk('.'):
        for filename in files:
            if filename == ".DS_Store":
                path = os.path.join(subdir, filename)
                
                print(f"Deleting: {path}")

                if os.remove(path):
                    print_failed("Unable to delete.")
                else:
                    deleted_files += 1

    print(f"Number of deleted files: {deleted_files}\n")


#   test1_check_file_size
#
#   Open firmware.json file and verify given conditions:
#   1) Check if firmware file under path "file" exists,
#   2) Check if real size of firmware file under path "file" is equal to its corresponding "size" value.
#
def test1_check_file_size():
    passed = 0
    failed = 0

    path_size_dict = dict()

    print("\nExecuting test1_check_file_size...")

    with open("firmware.json") as json_file:
        json_data = json.load(json_file)

        for obj in json_data:
            array = json_data[obj]
            for data in array:
                path = data['file']
                size = data['size']
                path_size_dict[path] = size

    for key in path_size_dict:
        try:
            expected_size = path_size_dict[key]
            file_size = os.stat(key).st_size

            if file_size == expected_size:
                print_passed("Found file in location: " + key + ", file size is correct.")
                passed += 1
            else:
                print_failed("Incorrect file size of: " + key + ". Should be: " + str(expected_size) + " but is: " + str(file_size))
                failed += 1
        except FileNotFoundError:
            print_failed("File not found: " + key)
            failed += 1

    print("Passed: [" + str(passed) + "], failed: [" + str(failed) + "]")
    print("test1 finished")


#   test2_directory_exist
#
#   Open firmware.json and verify given conditions:
#   1) Check if for "version" exists correctly named directory
#
#   Example:
#   If "version" is "1.2.2376.1" its firmware .gbl file
#   should be placed in directory: 1.2.1/1.2.2376.1/[.gbl file]
#
#   Base directory name is constructed by concatenating first, second and last value from "version" name.
#   Values are separated by "." (dots)
#
#   In our example:
#   1st value = 1
#   2nd value = 2
#   3rd value = 2376
#   4th value (last) = 1
#
#   So base directory is 1.2.1 and inner directory is the same as "version". It gives us  1.2.1/1.2.2376.1/
#
def test2_directory_exists():
    passed = 0
    failed = 0
    version_file_dict = dict()

    print("\nExecuting test2_directory_exists...")

    with open("firmware.json") as json_file:
        json_data = json.load(json_file)

        for obj in json_data:
            array = json_data[obj]
            for data in array:
                version = data['version']
                path = data['file']
                version_file_dict[version] = path

    for key in version_file_dict:
        tmp = key.split(".")
        ver_cmp = tmp[0] + "." + tmp[1] + "." + tmp[len(tmp)-1] + "/" + key

        if(os.path.isdir(ver_cmp)):
            passed += 1
            print_passed("Directory: " + ver_cmp + " exists.")
        else:
            failed += 1
            print_failed("Directory: " + ver_cmp + " not exist.")

    print("Passed: [" + str(passed) + "], failed: [" + str(failed) + "]")
    print("test2 finished")


#   test3_check_subdirectories
# 
#   Check if each base version directory contains only correctly named subdirectories.
#   Naming convetion is explained in description of test2
#
def test3_check_subdirectories():
    passed = 0
    failed = 0
    version_dirs = list()

    print("\nExecuting test3_check_subdirectories...")

    for tmp in os.listdir("."):
        if os.path.isdir(tmp) and tmp.replace(".", "").isdecimal():
            version_dirs.append(tmp)

    for dir in version_dirs:
        for subdir in os.listdir(dir):
            tmp = subdir.split(".")
            subdir_cmp = tmp[0] + "." + tmp[1] + "." + tmp[len(tmp)-1]

            if(dir == subdir_cmp and subdir.replace(".", "").isdecimal()):
                passed += 1
                print_passed(subdir + " directory inside of " + dir + " directory.")
            else:
                failed += 1
                print_failed(subdir + " directory inside of " + dir + " directory.")

    print("Passed: [" + str(passed) + "], failed: [" + str(failed) + "]")
    print("test3 finished")

#   find_unuseful_files_and_directories
#
#   Test verifies:
#   1) If there are any unuseful directories and subdirectories,
#   2) If there are any unuseful files in directories and subdirectories.
#
#   Files and directories found during tests should be removed.
#
#
def find_unuseful_files_and_directories():
    unuseful_files_or_dirs = 0
 
    print("\nLooking for unuseful files and directories...")

    for subdir, _, files in os.walk('.'):
        flatten_subdir = subdir[2:].replace(f"{os.sep}", "")

        for char in flatten_subdir:
            if not("." in char or str(char).isdecimal()):
                print_warning("Unuseful directory: " + subdir)
                unuseful_files_or_dirs += 1
                break

        for filename in files:
            if filename != "test.py" and filename != "firmware.json" and not filename.endswith(".gbl"):
                unuseful_files_or_dirs += 1
                print_warning("Unuseful file: " + subdir + os.sep + filename)

    if(unuseful_files_or_dirs > 0):
        print("Found [" + str(unuseful_files_or_dirs) + "] unuseful files or directories.\n")
    else:
        print("No unuseful files or directories found.\n")


def main():
    delete_DS_Store_files()
    test1_check_file_size()
    test2_directory_exists()
    test3_check_subdirectories()
    find_unuseful_files_and_directories()


if __name__ == "__main__":
    main()