<?php 


get_header();
$modules = zeta_get_option('homepage');
$default = array(
	'slider'        => false,
	'featured-post' => false,
	'movies'        => false,
	'ads'           => false,
	'ads-2'         => false,
	'ads-3'         => false,
	'tvshows'       => false,
	'seasons'       => false,
	'episodes'      => false,
	'top-imdb'      => false,
	'blog'          => false
);
$modules = (isset($modules['enabled'])) ? $modules['enabled'] : $default;


echo '<main>';
echo '<div class="module-wrapper">';

if(!empty($modules)){
	// Get template
	foreach($modules as $template => $template_name) {
		get_template_part('inc/parts/modules/'.$template);
	}
}

echo '</div>';
echo '<div class="clearfix"></div>';
echo '</main>';

get_template_part('inc/parts/sidebar', null, array('homep' => true));


get_footer();

