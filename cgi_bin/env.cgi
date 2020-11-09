#!/usr/bin/perl -wT
use strict;

print "CONTENT_LENGTH = $ENV{CONTENT_LENGTH}\n";
print "SCRIPT_NAME = $ENV{SCRIPT_NAME}\n";
# print "SERVER_NAME = $ENV{SERVER_NAME}\n";
# print "SERVER_PORT = $ENV{SERVER_PORT}\n";
print "HTTP_FROM = $ENV{HTTP_FROM}\n";
print "HTTP_USER_AGENT = $ENV{HTTP_USER_AGENT}\n";
