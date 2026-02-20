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
$imdbtit = ($posttype == 'tvshows') ?  __z('TMDb') :  __z('IMDb');
$imdbrat = zeta_isset($postmeta, 'imdbRating');
$imdbrat = ($imdbrat) ? $imdbrat : 'N/A';
$release = zeta_isset($postmeta, 'release_date');
$year = substr($release, 0, 4);
$year = ($year) ? $year : 'n/a';
$airdate = zeta_isset($postmeta, 'first_air_date');
$viewsco = zeta_isset($postmeta, 'zt_views_count');
$runtime = zeta_isset($postmeta, 'runtime');
$runtime = (isset($runtime)) ? $runtime.' '.__z('min') : 'n/a';
$watch = (isset($posttype) && $posttype == 'tvshows') ? __z('Watch TV Show') : __z('Watch Movie') ;
?>
<li>
            <div class="top-item">
              <div class="top-item-left">
                <span class="top-rank"><?php echo (isset($args['num'])) ? $args['num'] : null;?></span>
                <div class="top-poster">
				  <img class="top-poster-img" src="<?php echo omegadb_get_poster('', $post->ID); ?>">
                </div>
              </div>
              <div class="top-item-right">
                <div class="top-row">
                  <span class="top-name"><?php the_title();?></span>
                </div>
                <div class="top-row">
                  <span class="top-year"><?php  echo $year; ?></span> 
                  <span class="top-sep"></span> 
                  <span class="top-runtime"><?php echo $runtime;?></span>
                </div>
                <div class="top-row">
                  <div class="top-rating"> 
                  
                   <span class="top-rating-average"><i class="fa-solid fa-star"></i> <?php echo $imdbtit;?>: <?php echo $imdbrat;?></span>
                 </div>
               </div>
                <div class="top-row">
                  <div class="top-view"><a href="<?php the_permalink();?>"><span class="top-watch-btn"><?php echo $watch;?></span></a></div>
                </div>
              </div>
            </div>
</li>
