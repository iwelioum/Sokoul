
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

// All Postmeta
$classlinks = new ZetaLinks;
$postmeta = zeta_postmeta_tvshows($post->ID);
$adsingle = zeta_compose_ad('_zetaflix_adsingle');
// Movies Meta data
$trailer = zeta_isset($postmeta,'youtube_id');
$pviews  = zeta_isset($postmeta,'zt_views_count');
$player  = zeta_isset($postmeta,'players');
$player  = maybe_unserialize($player);
$images  = zeta_isset($postmeta,'imagenes');
$tviews  = ($pviews) ? sprintf( __z('%s Views'), $pviews) : __z('0 Views');
$backdrop = omegadb_get_poster('', $post->ID, '', 'zt_backdrop', 'original');
$poster = omegadb_get_poster('', $post->ID);

$keywords = get_the_term_list($post->ID, 'ztkeywords');
$quality = get_the_term_list( $post->ID, 'ztquality');

$pageplay = zeta_get_option('pagewatchplay');
$pageplay = get_permalink($pageplay);

$watch = get_query_var('watch');
$watch_location = zeta_get_option('watch_location', 'same');

//  Image
$dynamicbg  = omegadb_get_rand_image($images);
// Options
$player_ads = zeta_compose_ad('_zetaflix_adplayer');
$player_wht = zeta_get_option('playsize','regular');
// Sidebar
$sidebar = zeta_get_option('sidebar_position_single','right');

$season = get_query_var('season');
?>
<?php zeta_breadcrumb( $post->ID, 'tvshows', __z('TV Shows')); ?>
<?php  ZetaPlayer::viewer($post->ID, 'tv', $player, $trailer, $player_wht, $tviews, $player_ads, $dynamicbg); ?>
<?php get_template_part('inc/parts/single/listas/seasons_episodes'); ?>

<div class="content-body">
<main>
  <div class="content-info">
    <div class="content-col left">
      <div class="content-poster">
        <img class="poster-img" src="<?php echo $poster;?>" title="<?php the_title(); ?>">
      </div>
<?php echo ($trailer) ? '<div class="content-trailer">'.zeta_trailer_button($trailer).'</div>' : null;?>
      <div class="clearfix"></div>
    </div>
    <div class="content-col right">
      <div class="info-details">
        <div class="details-title">
          <h3><?php the_title(); ?></h3>
        </div>
        <div class="details-rating v2">
          <?php echo do_shortcode('[starstruck_shortcode]'); ?>
        </div>

        <div class="details-data">
          <span class="data-quality">HD</span>
          <!--<span class="data-imdb"><i class="fas fa-star"></i> 7.1</span>-->
          <?php if($d = get_post_meta($post->ID, 'imdbRating', true)) { ?>
            <span class="data-imdb v2">
                <?php echo __z('TMDb:').' '.$d;?>
                <b id="repimdb"></b>
        	    <?php if(current_user_can('administrator')) { ?><a data-id="<?php echo $post->ID; ?>" data-imdb="<?php echo zeta_isset($post->ID, 'ids'); ?>" id="update_imdb_rating"><i class="fa-solid fa-arrows-rotate"></i></a><?php } ?>
            </span>
          <?php }?>
          <?php 
		  if($d = get_post_meta($post->ID, 'episode_run_time', true)) echo "<span itemprop='duration' class='data-runtime'>{$d} ".__z('min')."</span>";?>
        </div>
        <div class="details-genre">
            <?php echo get_the_term_list($post->ID, 'genres', '', ', ', ''); ?>
        </div>
        <div class="details-desc">
    			<p><?php the_content();?></p>
    		</div>

		<div class="details-info">
          <div class="info-col">
			<?php echo zeta_istax($post->ID, 'ztcreator', __('Creator'));?>
             <?php echo zeta_istax($post->ID, 'ztcast', __('Stars'));?>
			  <?php echo zeta_istax($post->ID, 'ztnetworks', __('Networks'));?>
          </div>
          <div class="info-col">
            <?php echo zeta_istax($post->ID, 'ztcountry', __('Country'));?>
            <?php echo zeta_istax($post->ID, 'ztyear', __('Year'));?>
            <?php echo zeta_istax($post->ID, 'ztstudio', __('Studios'));?>
          </div>
          <div class="clearfix"></div>
        </div>
      </div>
    </div>

  </div>

<?php echo ($keywords) ? '<div class="content-keywords">'.$keywords.'</div>' : null; ?>
<?php echo (!$images || (ZETA_THEME_SOCIAL_SHARE != true OR zeta_is_true('permits', 'socl') != true)) ? "<div  class='content-sep px'></div>" : null;?>

  <?php zeta_social_share($post->ID); ?>
  
  <?php omegadb_get_gallery_images($images); ?>

  <?php if(ZETA_THEME_DOWNLOAD_MOD) get_template_part('inc/parts/single/links'); ?>

  <?php if(ZETA_THEME_RELATED) get_template_part('inc/parts/single/relacionados'); ?>

  <!-- Movie comments -->
  <?php get_template_part('inc/parts/comments'); ?>

  
  
  <div class="clearfix"></div>
</main>


<?php get_template_part('inc/parts/sidebar', null, array('ptyp' => 'tvshows')); ?>

<div class="clearfix"></div>
</div>
