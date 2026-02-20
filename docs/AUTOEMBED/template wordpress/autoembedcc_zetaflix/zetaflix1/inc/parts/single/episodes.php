
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
$postmeta = zeta_postmeta_episodes($post->ID);
$adsingle = zeta_compose_ad('_zetaflix_adsingle');
// Movies Meta data
$trailer = zeta_isset($postmeta,'youtube_id');
$pviews  = zeta_isset($postmeta,'zt_views_count');
$player  = zeta_isset($postmeta,'players');
$player  = maybe_unserialize($player);
$images  = zeta_isset($postmeta,'imagenes');
$gall 	 = ($images) ? true : null;
$tviews  = ($pviews) ? sprintf( __z('%s Views'), $pviews) : __z('0 Views');



$season = zeta_isset($postmeta,'seasonid');
$tmdbids  = zeta_isset($postmeta,'ids');
$temporad = zeta_isset($postmeta,'temporada');
$episode  = zeta_isset($postmeta,'episodio');

$ajaxep = zeta_get_option('playajaxep');
$ajaxep = (isset($ajaxep) && $post->post_type === 'episodes' && zeta_is_true('ajaxepdisplay','episodes')) ? true : null;

$poster = omegadb_get_poster('seasons', $post->ID);

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
// Dynamic Background

			

$tvepdata = zeta_get_option('tvepdata', 'default');
$tvepimages = zeta_get_option('tvepimages', 'default');

$dpid = null;

if( zeta_isset($postmeta,'ids') && ($tvepdata != 'default') OR $tvepimages != 'default'){
	if($tvepimages === 'inheritss' && zeta_isset($postmeta,'temporada')){
		$args = array( 'fields' => 'ids', 'post_type' => 'seasons', 'post_status' => 'publish', 'numberposts' => 1, 'meta_query' => array('relation' => 'AND',array('key' => 'ids','value' => zeta_isset($postmeta,'ids'),'compare' => '=',),array('key' => 'temporada','value' => zeta_isset($postmeta,'temporada'),'compare' => '=',),),); 
		$dpid = zeta_transient_getposts('zetaflix_ssid_'.zeta_isset($postmeta,'ids').zeta_isset($postmeta,'temporada'), $args, MINUTE_IN_SECONDS*60);
		$dpid = (isset($dpid[0]) && is_numeric($dpid[0])) ? $dpid[0] : null;
	}elseif($tvepimages === 'inherittv'){
		$args = array( 'fields' => 'ids', 'post_type' => 'tvshows', 'post_status' => 'publish', 'numberposts' => 1, 'meta_key'   => 'ids', 'meta_value' => zeta_isset($postmeta,'ids')); 
		$dpid = zeta_transient_getposts('zetaflix_tvid_'.zeta_isset($postmeta,'ids'), $args, MINUTE_IN_SECONDS*60);
		$dpid = (isset($dpid[0]) && is_numeric($dpid[0])) ? $dpid[0] : null;
	}
}



$dataPid = ($tvepdata != 'default') ? $dpid : $post->ID;
$imgId	= ($tvepimages != 'default') ? $dpid : $post->ID;

$backdrop = omegadb_get_backdrop('episodes', $imgId, 'original');
$poster = omegadb_get_poster('episodes', $imgId);
$tmdb = get_post_meta($post->ID,'ids',true);
$vidsrcepisodes = 'https://vidsrc.me/embed/tv?tmdb='.$tmdb.'&season='.$temporad.'&episode='.$episode.'';
$vidsrc2episodes = 'https://player.autoembed.cc/embed/tv/'.$tmdb.'/'.$temporad.'/'.$episode;
if ( ! empty( $player ) ) {
	$StreamalyPlayer = [
		count( $player ) + 1 =>
			[
				'name'   => 'VIP Player',
				'select' => 'superembed',
				'idioma' => 'en',
				'url'    => ''.$vidsrc2episodes.''
			]
	];
	$StreamalyPlayer2 = [
		count( $player ) + 1 =>
			[
				'name'   => 'Vidsrc',
				'select' => 'superembed',
				'idioma' => 'en',
				'url'    => ''.$vidsrcepisodes.''
			]
	];

	$player = array_merge( $player, $StreamalyPlayer, $StreamalyPlayer2 );
} else {
	$player = [
		0 =>
			[
				'name'   => 'VIP Player',
				'select' => 'superembed',
				'idioma' => 'en',
				'url'    => ''.$vidsrc2episodes.''
			]
	];
	$player2 = [
		0 =>
			[
				'name'   => 'Vidsrc',
				'select' => 'superembed',
				'idioma' => 'en',
				'url'    => ''.$vidsrcepisodes.''
			]
	];
	$player = array_merge( $player, $player2 );
}
 ?>

<?php zeta_breadcrumb( $post->ID, 'episodes', __z('Episodes')); ?>


<?php echo ($tvepdata === 'inherit' && !$dpid && (current_user_can('editor') || current_user_can('administrator') )) ? '<div class="site-notice error">Season does not have a <span class="hl">parent tvshow</span>.</div>' : null; ?>

<?php ZetaPlayer::viewer($post->ID, 'ep', $player, $trailer, $player_wht, $tviews, $player_ads, $dynamicbg); ?>
<?php require_once( ZETA_DIR.'/inc/parts/single/listas/episode_navigation.php'); ?>
<?php get_template_part('inc/parts/single/listas/seasons', null, array('ajaxep' => $ajaxep)); ?>
<div class="content-body">
<main>
  <div class="content-info">
  	<?php if($poster){?>
    <div class="content-col left">
      <div class="content-poster">
        <img class="poster-img" src="<?php echo $poster;?>" title="<?php the_title(); ?>">
      </div>        
      <div class="clearfix"></div>
    </div>
	<?php }?>
    <div class="content-col right">
      <div class="info-details">
        <div class="details-title">
          <h3><?php the_title(); ?></h3>
        </div>

        <div class="details-rating v2">
          <?php echo do_shortcode('[starstruck_shortcode]'); ?>
        </div>
        <div class="details-data">
          <?php echo ($quality) ? '<span class="data-quality">'.strip_tags($quality).'</span>' : null;?>
          <?php 
		  if($d = get_post_meta($dataPid, 'episode_run_time', true)) echo "<span itemprop='duration' class='data-runtime'>{$d} ".__z('min')."</span>";?>
        </div>
        <div class="details-genre">
		<?php echo zeta_istax($dataPid, 'genres', '');?>
        </div>

        <div class="details-desc">
    			<p><?php the_content();?></p>
    		</div>
		<div class="details-info">
          <div class="info-col">		  
		  <?php  $episode_name = zeta_isset($postmeta,'episode_name'); if($episode_name){?><p><strong><?php echo __z('Episode Title');?>:</strong><a><?php echo $episode_name;?></a></p><?php }?>
			<?php echo zeta_istax($dataPid, 'ztcreator', __('Creator'));?>
             <?php echo zeta_istax($dataPid, 'ztcast', __('Stars'));?>
			  <?php echo zeta_istax($dataPid, 'ztnetworks', __('Networks'));?>
          </div>
          <div class="info-col">
			<?php $series_name = zeta_isset($postmeta,'serie'); if($episode_name){?> <p><strong><?php echo __z('Serie');?>:</strong><a href="<?php echo get_permalink($dataPid);?>"><?php echo $series_name;?></a></p><?php }?>
            <?php echo zeta_istax($dataPid, 'ztcountry', __('Country'));?>
            <?php echo zeta_istax($dataPid, 'ztyear', __('Year'));?>
            <?php echo zeta_istax($dataPid, 'ztstudio', __('Studios'));?>
          </div>
          <div class="clearfix"></div>
        </div>
      </div>
    </div>

  </div>


<?php echo ($keywords) ? '<div class="content-keywords">'.$keywords.'</div>' : null; ?>
<?php echo (!$images || (ZETA_THEME_SOCIAL_SHARE != true OR zeta_is_true('permits', 'socl') != true)) ? "<div  class='content-sep px'></div>" : null;?>
  <?php zeta_social_share($post->ID); ?>
  
  <?php omegadb_get_gallery_images($images);?>


  <?php if(ZETA_THEME_DOWNLOAD_MOD) get_template_part('inc/parts/single/links', '', array('gall' => $gall)); ?>

  <?php if(ZETA_THEME_RELATED) get_template_part('inc/parts/single/relacionados'); ?>

  <!-- Movie comments -->
  <?php get_template_part('inc/parts/comments'); ?>

  
  
  <div class="clearfix"></div>
</main>
<?php get_template_part('inc/parts/sidebar', null, array('ptyp' => 'episodes')); ?>
<div class="clearfix"></div>
</div>



