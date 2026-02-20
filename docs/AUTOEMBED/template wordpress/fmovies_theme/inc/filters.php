<?php
/**
 * Ajax filters
 *
 * @package fmovie
 */

function filter_posts() {
  $catSlug = $_POST['category'] ?? null;
  $meta_key = $_POST['meta'] ?? null;
  $orderby = $_POST['orderby'] ?? null;
  $exclude = $_POST['exclude'] ?? null;
  

  $ajaxposts = new WP_Query([
    'post_type' => 'post',
	'post_status' => 'publish',
    'posts_per_page' => get_option('posts_per_page'),
    'category_name' => $catSlug,
    'cat' => $exclude,
    'meta_key' => $meta_key,
    'orderby' => $orderby, 
    'order' => 'DESC',
	'no_found_rows' => true
  ]);
  $response = '';

  if($ajaxposts->have_posts()) {
    while($ajaxposts->have_posts()) : $ajaxposts->the_post();
      $response .= get_template_part( 'template-parts/content/content', 'loop' );
    endwhile;
  } else {
    $response = 'empty';
  }

  echo $response;
  exit;
  }
add_action('wp_ajax_filter_posts', 'filter_posts');
add_action('wp_ajax_nopriv_filter_posts', 'filter_posts');

add_action('wp_footer', 'filter_posts_ajaxurl', 5);
function filter_posts_ajaxurl() {
$admin_url = esc_url(admin_url('admin-ajax.php'));
$admin_url = str_replace("/", "\/", $admin_url);
echo '
<script type="text/javascript">var ajax_url = "' . $admin_url . '";</script>
';
}
