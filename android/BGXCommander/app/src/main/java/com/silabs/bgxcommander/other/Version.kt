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
package com.silabs.bgxcommander.other

class Version constructor(versionString: String) : Any(), Comparable<Any> {
    var major: Int
    var minor: Int
    var build: Int
    var revision: Int

    init {
        val pieces = versionString.split("\\.".toRegex()).toTypedArray()
        if (4 == pieces.size) {
            major = pieces[0].toInt()
            minor = pieces[1].toInt()
            build = pieces[2].toInt()
            revision = pieces[3].toInt()
        } else {
            throw RuntimeException("Invalid version string: $versionString")
        }
    }

    override fun compareTo(other: Any): Int {
        return this.compareTo(other as Version)
    }

    operator fun compareTo(v: Version): Int {
        return if (major == v.major) {
            if (minor == v.minor) {
                if (build == v.build) {
                    if (revision == v.revision) {
                        0
                    } else {
                        revision - v.revision
                    }
                } else {
                    build - v.build
                }
            } else {
                minor - v.minor
            }
        } else {
            major - v.major
        }
    }


}