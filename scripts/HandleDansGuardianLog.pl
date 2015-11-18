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
	my ($analysis_engine_url) = @_;
	my ($relevant_categories_url) = "${analysis_engine_url}relevantCategories/";
	my $get_result = $ua->request(GET $relevant_categories_url);
	my $status_code = $get_result->{'_rc'};
	if ($status_code != 200) {
		print STDERR "ERROR: GET on '$relevant_categories_url' returned status $status_code\n";
		return;
	}
	my $categories_json = $get_result->content;
	my $categories = decode_json $categories_json;
	foreach $category (values $categories->{'categories'}) {
		$relevant_url_categories{$category} .= "";
	}
}

sub handle_records_from_stream {
	my ($fh, $analysis_engine_url) = @_;

	fetch_relevant_url_categories $analysis_engine_url;
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
	my ($file_name, $analysis_engine_url) = @_;
	open INPUT_FILE, "<$file_name" or die "Cannot open file $INPUT_FILE\n";
	handle_records_from_stream(*INPUT_FILE, $analysis_engine_url);
	close INPUT_FILE;
}

my $analysis_engine_url = 'http://localhost:8081/analysisEngine/';
GetOptions ('analysisEngineURL=s' => \$analysis_engine_url) or die "Usage: $0 [--analysisEngineURL <URL>] [<input file>]";
my $input_file = $ARGV[0];
if ($input_file) {
	if (! -e $input_file) {
		die "Input file '$input_file' does not exist\n";
	}
	do {
		handle_records_from_file($input_file, $analysis_engine_url);
	} while (-p $input_file);
	
} else {
	handle_records_from_stream(STDIN, $analysis_engine_url);
}
