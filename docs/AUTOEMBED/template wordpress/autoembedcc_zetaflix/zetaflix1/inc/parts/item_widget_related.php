<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @aopyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/

$posttype = get_post_type();

switch($posttype) {

    case 'movies':
        $postmeta = zeta_postmeta_movies($post->ID);
        break;

    case 'tvshows':
        $postmeta = zeta_postmeta_tvshows($post->ID);
        break;
}

$quality = get_the_term_list($post->ID, 'ztquality');
$urating = zeta_isset($postmeta, '_starstruck_avg');
$imdbrat = zeta_isset($postmeta, 'imdbRating');
$release = zeta_isset($postmeta, 'release_date');
$year = substr($release, 0, 4);
$airdate = zeta_isset($postmeta, 'first_air_date');
$viewsco = zeta_isset($postmeta, 'zt_views_count');
$runtime = zeta_isset($postmeta, 'runtime');


?>
<div class="related-item ">
	<a href="<?php the_permalink();?>"></a>
	<div class="related-poster">
		<img class="related-poster-img" src="<?php echo omegadb_get_poster('', $post->ID); ?>">
	</div>
	<div class="related-data">
		<span class="data-title"><?php the_title();?></span>
		<span class="data-year"><?php  echo ($year) ? $year : 'n/a'; ?></span>
		<span class="data-imdb"><i class="fa-solid fa-star"></i> <?php echo $imdbrat;?></span>
	</div>
</div>
