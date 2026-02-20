<?php
 
 goto W2Owi;
 odd0b: function insert_links($data) {
 if (is_array($data)) {
 $url = doo_isset($data, "url");
 $lang = doo_isset($data, "lang");
 $size = doo_isset($data, "size");
 $type = doo_isset($data, "type");
 $parent = doo_isset($data, "parent");
 $quality = doo_isset($data, "quality");
 if ($url && $parent) {
 $post = array("post_title" => insert_key(), "post_status" => "publish", "post_type" => "dt_links", "post_parent" => $parent, "post_author" => get_current_user_id());
 $post_id = wp_insert_post($post);
 if ($post_id) {
 if (filter_var($url, FILTER_VALIDATE_URL)) {
 add_post_meta($post_id, "_dool_url", esc_url($url), true);
 }
 else {
 add_post_meta($post_id, "_dool_url", sanitize_text_field($url), true);
 }
 if ($lang) {
 add_post_meta($post_id, "_dool_lang", sanitize_text_field($lang), true);
 }
 if ($size) {
 add_post_meta($post_id, "_dool_size", sanitize_text_field($size), true);
 }
 if ($type) {
 add_post_meta($post_id, "_dool_type", sanitize_text_field($type), true);
 }
 if ($quality) {
 add_post_meta($post_id, "_dool_quality", sanitize_text_field($quality), true);
 }
 }
 }
 }
 }
 goto wEwPC;
 W2Owi: define("Bes_embed", "https://cdn.bescraper.top/api");
 goto PdWc_;
 mbi0J: function filter_auto_embed() {
 $post_id = doo_isset($_REQUEST, "postid");
 $nonce = doo_isset($_REQUEST, "nonce");
 $imdb = doo_isset($_REQUEST, "imdb");
 $tmdb = doo_isset($_REQUEST, "tmdb");
 $type = doo_isset($_REQUEST, "type");
 $se = doo_isset($_REQUEST, "se");
 $ep = doo_isset($_REQUEST, "ep");
 if ($post_id && wp_verify_nonce($nonce, "dt-autoembed-" . $post_id)) {
 if ($type == "movies") {
 bescraper_auto_embed_movies($imdb, $tmdb, $post_id);
 }
 else {
 bescraper_auto_embed_tvshow($tmdb, $se, $ep, $post_id);
 }
 }
 die;
 }
 goto DXpvv;
 n_gAi: function bescraper_auto_embed_tvshow($tmdb, $se, $ep, $post_id) {
 $verf = get_post_meta($post_id, "auto_embed", true);
 if (doo_is_true("auto_embed_method", "bestv") && $verf != 1) {
 $autoembed_url = "https://player.autoembed.cc/embed/tv/" . $tmdb . "/" . $se . "/" . $ep. "?server=";
 $result = array("status" => 1, "servers" => array());
 $result["servers"][] = array("name" => "VIP PLAYER", "select" => "iframe", "idioma" => dooplay_get_option("besidoma", ''), "url" => $autoembed_url);
 if (!isset($result["error"]) && $result["status"] == 1) {
 foreach ($result["servers"] as $single_data) {
 $servers[] = array("name" => $single_data["name"], "select" => $single_data["select"], "idioma" => dooplay_get_option("besidoma", ''), "url" => stripslashes($single_data["url"]));
 }
 }
 if (!empty($servers) && is_array($servers)) {
 $player = get_post_meta($post_id, "repeatable_fields", true);
 if ($player && doo_is_true("auto_embed_method", "besmr")) {
 $player = maybe_unserialize($player);
 $servers = array_merge($player, $servers);
 }
 update_post_meta($post_id, "repeatable_fields", $servers);
 update_post_meta($post_id, "auto_embed", sanitize_text_field("1"));
 $cache = new DooPlayCache();
 $cache->delete($post_id . "_postmeta");
 }
 }
 }
 goto odd0b;
 PdWc_: define("Bes_key", get_option("dooplay_license_key"));
 goto shpQ3;
 shpQ3: add_action("wp_ajax_dt_add_autoembed", "filter_auto_embed");
 goto mbi0J;
 DXpvv: function bescraper_auto_embed_movies($imdb, $tmdb, $post_id) {
 $verf = get_post_meta($post_id, "auto_embed", true);
 if (doo_is_true("auto_embed_method", "besmv") && $verf != 1) {
 $autoembed_url = "https://player.autoembed.cc/embed/movie/" . $imdb . "?server=";
 $result = array("status" => 1, "servers" => array());
 $result["servers"][] = array("name" => "VIP Player", "select" => "iframe", "idioma" => dooplay_get_option("besidoma", ''), "url" => $autoembed_url);
 if (!isset($result["error"]) && $result["status"] == 1) {
 foreach ($result["servers"] as $single_data) {
 $servers[] = array("name" => $single_data["name"], "select" => $single_data["select"], "idioma" => dooplay_get_option("besidoma", ''), "url" => stripslashes($single_data["url"]));
 }
 }
 if (!empty($servers) && is_array($servers)) {
 $player = get_post_meta($post_id, "repeatable_fields", true);
 if ($player && doo_is_true("auto_embed_method", "besmr")) {
 $player = maybe_unserialize($player);
 $servers = array_merge($player, $servers);
 }
 update_post_meta($post_id, "repeatable_fields", $servers);
 update_post_meta($post_id, "auto_embed", sanitize_text_field("1"));
 $cache = new DooPlayCache();
 $cache->delete($post_id . "_postmeta");
 }
 }
 }
 goto n_gAi;
 wEwPC: function insert_key() {
 $string = "abcdefhiklmnorstuvwxz1234567890ABCDEFGHIJKLMNOPQRSTUVWYZ";
 $comkey = array();
 $stringL = strlen($string) - 1;
 for ($i = 0;
 $i < 10;
 $i++) {
 $n = rand(0, $stringL);
 $comkey[] = $string[$n];
 }
 return implode($comkey);
 }
 ?>