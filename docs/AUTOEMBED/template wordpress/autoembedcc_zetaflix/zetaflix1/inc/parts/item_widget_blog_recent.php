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
$num++;
?>
<li>
  <div class="top-item">
    <div class="top-item-left">
      <span class="top-rank"> <?php echo $args['num'];?> </span>
      <div class="top-poster">
        <img class="top-poster-img" src="
		<?php echo omegadb_get_poster('', $post->ID); ?>">
      </div>
    </div>
    <div class="top-item-right">
      <div class="top-row">
        <span class="top-name"> <?php the_title();?> </span>
      </div>
      <div class="top-row">
        <span class="top-year"> <?php  echo ($year) ? $year : 'na/a'; ?> </span>
        <span class="top-sep"></span>
        <span class="top-runtime"> <?php echo ($runtime) ? $runtime.' '.__z('min') : 'na/a';?> </span>
      </div>
      <div class="top-row">
        <div class="top-rating">
          <span class="top-rating-average">
            <i class="fa-solid fa-star"></i> IMDb: <?php echo $imdbrat;?> </span>
        </div>
      </div>
      <div class="top-row">
        <div class="top-view">
          <a href="
							<?php the_permalink();?>">
            <span class="top-watch-btn"> <?php _z('Watch Movie');?> </span>
          </a>
        </div>
      </div>
    </div>
  </div>
</li>