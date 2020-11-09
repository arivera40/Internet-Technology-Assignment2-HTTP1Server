#!/usr/bin/perl -wT
use CGI qw(:standard);
use strict;
use Fcntl qw(:flock :seek);

my $cgi = CGI->new(<STDIN>);

my $outfile = $cgi->param("file");
my $desired_line = $cgi->param("line");

# open the file for reading
open(IN, "$outfile") or &dienice("Couldn't open $outfile: $!");
# set a shared lock
flock(IN, LOCK_SH); 
# then seek the beginning of the file
seek(IN, 0, SEEK_SET);

# declare the totals variables
my ($total_votes);

$total_votes = 0;

while (my $rec = <IN>) {
   chomp($rec);
   $total_votes = $total_votes + 1;
   if ($total_votes == $desired_line) {
      print $rec;
      close(IN);
      exit;
   }
}
close(IN);
print "Sorry, I think the line number you entered is greater than the number of lines in the file.\n";


sub dienice {
    my($msg) = @_;
    print h2("Error");
    print $msg;
    exit;
}