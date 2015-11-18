#!/usr/bin/perl
use JSON;
use Data::Dumper;
use LWP::UserAgent;
use HTTP::Request::Common;
use Getopt::Long;

my %relevant_url_categories;
my $ua = LWP::UserAgent->new;

sub filter_relevant_url_categories {
	my $listS = shift;
	my @list = @$listS;
	return grep { exists( $relevant_url_categories{$_} ) } @{list};
}

sub transform_log_record ($) {
	my ($log_record) = @_;
	my $log_message = decode_json $log_record;
	my $url = $log_message->{'url'};
	if (!$log_message->{'tagset'}->{'username'}) {
		print "WARNING: No user name\n";
		return undef;
	}
	my $username = (keys $log_message->{'tagset'}->{'username'}) [0];
	my @url_categories_logged = keys $log_message->{'requesttags'}->{'urlcategory'};
	my @relevant_url_categories_logged = filter_relevant_url_categories \@url_categories_logged;
	if (!@relevant_url_categories_logged) {
		return undef;
	}

	my $analysis_event = {
		'loginID' => $username,
		'categories' => [@relevant_url_categories_logged],
		'url' => $url
	};
	return encode_json $analysis_event;
}

sub fetch_relevant_url_categories {
	my ($goals_url) = @_;
	my $get_result = $ua->request(GET $goals_url);
	my $status_code = $get_result->{'_rc'};
	if ($status_code != 200) {
		print STDERR "ERROR: GET on '$goals_url' returned status $status_code\n";
		return;
	}
	my $goals_json = $get_result->content;
	my $goals = decode_json $goals_json;
	foreach $goal (values $goals->{'_embedded'}->{'Goals'}) {
		foreach $category (values $goal->{'categories'}) {
			$relevant_url_categories{$category} .= "";
		}
	}
}

sub handle_records_from_stream {
	my ($fh, $goals_url, $analysis_engine_url) = @_;

	fetch_relevant_url_categories $goals_url;
	while (<$fh>) {
		my $analysis_event_json = transform_log_record $_;

		if ($analysis_event_json) {
			my $post_result = $ua->request(POST $analysis_engine_url, Content_Type => 'application/json', Content => $analysis_event_json);
			my $status_code = $post_result->{'_rc'};
			if ($status_code != 200) {
				print STDERR "ERROR: POST to '$analysis_engine_url' returned status $status_code\n";
			}
		}
	}
}

sub handle_records_from_file {
	my ($file_name, $goals_url, $analysis_engine_url) = @_;
	open INPUT_FILE, "<$file_name" or die "Cannot open file $INPUT_FILE\n";
	handle_records_from_stream(*INPUT_FILE, $goals_url, $analysis_engine_url);
	close INPUT_FILE;
}

my $goals_url = 'http://localhost:8080/goals/';
my $analysis_engine_url = 'http://localhost:8080/analysisEngine/';
GetOptions ('goalsURL=s' => \$goals_url, 'analysisEngineURL=s' => \$analysis_engine_url) or die "Usage: $0 [--goalsURL <URL>] [--analysisEngineURL <URL>] [<input file>]";
my $input_file = $ARGV[0];
if ($input_file) {
	if (! -e $input_file) {
		die "Input file '$input_file' does not exist\n";
	}
	do {
		handle_records_from_file($input_file, $goals_url, $analysis_engine_url);
	} while (-p $input_file);
	
} else {
	handle_records_from_stream(STDIN, $goals_url, $analysis_engine_url);
}
