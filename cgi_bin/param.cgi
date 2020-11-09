#!/usr/bin/perl -wT
use strict;

use CGI qw(:standard);

my $cgi = CGI->new(<STDIN>);

foreach my $param ($cgi->param()) {
   foreach my $value ($cgi->param($param)) {
      printf("%s=%s\n",$param,$value,
      );
   }
}