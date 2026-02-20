<?php
$act = array(
  "success" => true,
  "license" => "valid",
  "item_name" => "DooPlay",
  "user_id" => "333",
  "payment_id" => "#333",
  "customer_name" => "autoembed.cc",
  "customer_email" => "user@autoembed.cc",
  "license_limit" => "10000000000",
  "site_count" => "652",
  "activations_left" => 1000000000,
  "expires" => "lifetime",
);
$checkar = array(
  "response" => true,
  "status" => "1",
  "credits" => "10000000000",
  "requests" => "125486",
  "unlimited" => "0",
  "website" => "0",
  "dbmovies" => "active",
  "used_credits" => "2000",
  "autoembed" => "100000",
);
$statsar = array(
   "response" => true, 
   "status" => "1", 
   "total_caching" => 26243, 
   "total_sites" => 9896, 
   "total_licenses" => 11975, 
   "total_bannded_licenses" => 926, 
   "total_requests" => 45750767, 
   "total_credits" => 51649044629, 
   "total_used_credits" => 1012100, 
   "total_autoembed" => [
         "movies" => 0, 
         "episodes" => 0 
      ], 
   "last_cache" => "2023-11-26 04:09:21  CEST +2" 
);

$edd = (isset($_GET['edd_action']) && $_GET['edd_action']) ? $_GET['edd_action'] : '0';
$activate_license = $edd == "activate_license";
$check_license = $edd == "check_license";
$deactivate_license = $edd == "deactivate_license";

if ($activate_license != 0) {
  http_response_code(200);
  echo json_encode($act);
  exit;
}
if ($check_license != 0) {
  http_response_code(200);
  echo json_encode($act);
  exit;
}
if ($deactivate_license != 0) {
  http_response_code(200);
  echo json_encode(array("license" => "deactivated"));
  exit;
}
if (isset($_GET['check'])) {
  http_response_code(200);
  echo json_encode($checkar);
  exit;
}
if (isset($_GET['get_version'])) {
  http_response_code(200);
  echo json_encode(array(
    "0" => "dooplay",
    "success" => true,
    "license" => "valid",
    "new_version" => "2.5.5.1",
    //"package" => "https =>//bescraper.cf/app/download/?version=2.5.5.1&key=6bb18caf566b52cad79435b982d88b46",
    "package" => "https://autoembed.cc/assets/dooplay/dooplay-latest-PHP-version.zip",
    "name" => "dooplay",
    "slug" => "dooplay",
    "url" => "",
    "last_updated" => "2021-06-23 02:15:12",
    "sections" => "s:4:\"null\";"
  ));
  exit;
}

if (isset($_GET['process']) && ($_GET['process'] == 'activate')) {
  http_response_code(200);
    $response_data = array(
        'success' => true,
        'status' => 'active',
        'domain' => '',
        'siteid' => '13436',
        'license' => '6bb18caf566b52cad79435b982d88b46'
    );
    $serialized_response = serialize($response_data);
    header('Content-Type: application/json');
    echo $serialized_response;
  exit;
}

if (isset($_GET['stats'])) {
  http_response_code(200);
  echo json_encode($statsar);
  exit;
} else {
  http_response_code(404);
  echo json_encode(
    array(
      "success" => true,
      "license" => "valid",
      "response" => true,
      "status" => "1",
    )
  );
  exit;
}
