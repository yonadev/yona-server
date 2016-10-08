#!/usr/bin/perl
#*******************************************************************************
# Copyright (c) 2016 Stichting Yona Foundation
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#*******************************************************************************
use JSON;
use Data::Dumper;
use LWP::UserAgent;
use HTTP::Request::Common;
use Getopt::Long;

my $analysis_engine_url = 'http://localhost:8081/';
my $categories_refresh_interval = 300;
my $relevant_url_categories_load_time = 0;
my %relevant_url_categories;
my $ua = LWP::UserAgent->new;

sub log_message {
	my ($level, $message) = @_;
	my $date_time = localtime();
	print "$date_time $level $message\n"
}

sub log_info {
	my ($message) = @_;
	log_message("INFO", $message);
}

sub log_error {
	my ($message) = @_;
	log_message("ERROR", $message);
}

sub log_warning {
	my ($message) = @_;
	log_message("WARNING", $message);
}

sub log_debug {
	my ($message) = @_;
	log_message("DEBUG", $message);
}

sub filter_relevant_url_categories {
	my $listS = shift;
	my @list = @$listS;
	return grep { exists( $relevant_url_categories{$_} ) } @{list};
}

sub transform_log_record ($) {
	my ($log_message) = @_;
	my $url = $log_message->{'url'};
	if (!$log_message->{'tagset'}->{'username'}) {
		log_warning "No user name";
		return undef;
	}
	if (!$log_message->{'requesttags'}->{'urlcategory'}) {
		# Unclassified request. Probably an HTTPS site
		return undef;
	}

	my @url_categories_logged = keys $log_message->{'requesttags'}->{'urlcategory'};
	my @relevant_url_categories_logged = filter_relevant_url_categories \@url_categories_logged;
	if (!@relevant_url_categories_logged) {
		# Categories are not relevant
		return undef;
	}

	my $analysis_event = {
		'categories' => [@relevant_url_categories_logged],
		'url' => $url
	};
	return encode_json $analysis_event;
}

sub fetch_relevant_url_categories {
	if ($relevant_url_categories_load_time + $categories_refresh_interval > time()) {
		# Still fresh
		return;
	}
	%relevant_url_categories = (); # Clear the current set
	my ($relevant_categories_url) = "${analysis_engine_url}relevantSmoothwallCategories/";
	log_info "Loading relevant categories from $relevant_categories_url";
	my $get_result = $ua->request(GET $relevant_categories_url);
	my $status_code = $get_result->{'_rc'};
	if ($status_code != 200) {
		log_error "GET on '$relevant_categories_url' returned status $status_code";
		return;
	}
	my $categories_json = $get_result->content;
	my $categories = decode_json $categories_json;
	foreach $category (values $categories->{'categories'}) {
		$relevant_url_categories{$category} .= "";
	}
	foreach $category (keys %relevant_url_categories) {
		log_info "Loaded category $category";
	}
	$relevant_url_categories_load_time = time();
	log_info "Finished loading relevant categories";
}

sub handle_records_from_stream {
	my ($fh) = @_;

	fetch_relevant_url_categories; # To check the connection, before receiving the first event
	while (<$fh>) {
		fetch_relevant_url_categories
		my $log_record = $_;
		my $log_message = eval { decode_json($log_record) };
		if ($@)
		{
			log_warning "decode_json failed, invalid json in log record. error:$@. Log record: $log_record";
		}
		else
		{
			my $analysis_event_json = transform_log_record $log_message;

			if ($analysis_event_json) {
				my $user_dn = (keys $log_message->{'tagset'}->{'username'}) [0];
				my $vpn_login_id = substr $user_dn, 3, 36;
				my $user_anonymized_url = "${analysis_engine_url}userAnonymized/${vpn_login_id}/networkActivity/";
				my $post_result = $ua->request(POST $user_anonymized_url, Content_Type => 'application/json', Content => $analysis_event_json);
				my $status_code = $post_result->{'_rc'};
				if ($status_code != 200) {
					log_error "POST to '$user_anonymized_url' returned status $status_code";
				}
			}
		}
	}
}

sub handle_records_from_file {
	my ($file_name) = @_;
	log_info "Reading records from '$file_name'";
	open INPUT_FILE, "<$file_name" or die "Cannot open file $INPUT_FILE\n";
	handle_records_from_stream(*INPUT_FILE);
	close INPUT_FILE;
}

$| = 1; # Make STDOUT unbuffered
GetOptions ('analysisEngineURL=s' => \$analysis_engine_url,
	'categoriesRefreshInterval=i' => \$categories_refresh_interval)
or die "Usage: $0 [--analysisEngineURL <URL>] [--categoriesRefreshInterval <interval in seconds>] [<input file>]";

my $input_file = $ARGV[0];
if ($input_file) {
	if (! -e $input_file) {
		die "Input file '$input_file' does not exist\n";
	}
	do {
		handle_records_from_file($input_file);
	} while (-p $input_file);
	
} else {
	log_info "Reading records from STDIN";
	handle_records_from_stream(STDIN);
}
