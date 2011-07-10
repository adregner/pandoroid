#!/usr/bin/env python
#
# (c) 2011 Andrew Regner <andrew@aregner.com>
#
# Usage: ./tools/update-pandora-api-keys.py /path/to/source/file.h [...]
#
# This script is intended to port keys from a C source file to the PandoraKeys.java file
# used for the Pandoroid project.  Such as the crypt_key_{input,output}.h files in the
# pianobar/libpiano source.

import os
import sys
import re

#init
if len(sys.argv) < 2:
	print >> sys.stderr, "Usage: ./tools/update-pandora-api-keys.py /path/to/source/file.h [...]"
	sys.exit(1)

if '/tools/' not in sys.argv[0]:
	print >> sys.stderr, "Please run this from the project's root directory"
	sys.exit(1)

key_list = {}

#main
for kf in sys.argv[1:]:
	
	if not os.path.isfile(kf):
		print >> sys.stderr, "%s is not a file" % kf
		sys.exit(1)
	
	state = ''
	
	for line in open(kf, 'r').readlines():
		# haven't found the name of a key yet
		if state == '':
			m = re.search("uint32_t ((in|out)_key_[ps])", line)
			if m:
				key_name = m.group(1)
				key_data = ''
				state = 'reading-key'
		
		# reading the values for this key
		elif state == 'reading-key':
		
			if '};' in line:
				#end of key data
				key_data = re.sub('0x[0-9A-F]{8}', lambda m: "%sL" % m.group(0), key_data)
			
				if key_name[-1] == 's':
					key_data = "public static final long[][] "+ key_name +" = {{\n" + key_data + "\t}};"
				else:
					key_data = "public static final long[] "+ key_name +" = {\n" + key_data + "\t};"
			
				key_list[key_name] = key_data
				state = ''
		
			else:
				key_data += line

if len(key_list) != 4:
	print >> sys.stderr, "Did not get all 4 keys needed.  Only saw: " + repr(key_list.keys())

else:
	fd = open("src/com/aregner/pandora/PandoraKeys.java", 'w')
	print >> fd, """/* Pandoroid Radio - open source pandora.com client for android
 * Copyright (C) 2011  Andrew Regner <andrew@aregner.com>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

/* 
 * These keys were extracted from the Pithos source code, credited to ZigZagJoe.
 * 
 * Pithos is released under the GNU GPL v3, Copyright (C) 2010 Kevin Mehall <km@kevinmehall.net>
 */

package com.aregner.pandora;

public final class PandoraKeys {
	%(out_key_p)s
	
	%(out_key_s)s
	
	%(in_key_p)s
	
	%(in_key_s)s
}
""" % key_list
	fd.close()
