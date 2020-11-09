#!/usr/bin/perl -wT
use CGI qw(:standard);
use URI::Escape;

my $cgi = CGI->new(<STDIN>);
foreach my $param ($cgi->param()) {
   foreach my $value ($cgi->param($param)) {
      printf("%s = %s\n",
         uri_unescape($param),
         $value,
      );
   }
}