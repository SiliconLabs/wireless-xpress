/*
 * Copyright 2018-2019 Silicon Labs
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * {{ http://www.apache.org/licenses/LICENSE-2.0}}
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.silabs.bgxcommander;

public class Version extends Object implements Comparable {

    public Integer major;
    public Integer minor;
    public Integer build;
    public Integer revision;

    Version(String versionString)
    {
        String[] pieces = versionString.split("\\.");

        if (4 == pieces.length) {

            major = Integer.parseInt(pieces[0]);
            minor = Integer.parseInt(pieces[1]);
            build = Integer.parseInt(pieces[2]);
            revision = Integer.parseInt(pieces[3]);
        } else {
            // raise an exception
            throw new RuntimeException("Invalid version string: "+versionString);
        }

    }


    @Override
    public int compareTo(Object other) {
        return ((Version)this).compareTo((Version)other);
    }

    public int compareTo(Version v) {
        if (this.major == v.major ) {
            if (this.minor == v.minor) {
                if (this.build == v.build) {
                    if (this.revision == v.revision) {
                        return 0;
                    } else {
                        return this.revision - v.revision;
                    }
                } else {
                    return this.build - v.build;
                }
            } else {
                return this.minor - v.minor;
            }
        } else {
            return this.major - v.major;
        }
    }
}
