
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
$postmeta = zeta_postmeta_seasons($post->ID);
$adsingle = zeta_compose_ad('_zetaflix_adsingle');

// Movies Meta data
$trailer = zeta_isset($postmeta,'youtube_id');
$pviews  = zeta_isset($postmeta,'zt_views_count');
$player  = zeta_isset($postmeta,'players');
$player  = maybe_unserialize($player);
$images  = zeta_isset($postmeta,'imagenes');
$tviews  = ($pviews) ? sprintf( __z('%s Views'), $pviews) : __z('0 Views');


$keywords = get_the_term_list($post->ID, 'ztkeywords');
$quality = get_the_term_list( $post->ID, 'ztquality');




$pageplay = zeta_get_option('pagewatchplay');
$pageplay = get_permalink($pageplay);

$watch = (is_singular(array('movies','tvshows','seasons')) && !empty($_GET['watch']) && is_numeric($_GET['watch'])) ? 'watch' : '';


//  Image
$dynamicbg  = omegadb_get_rand_image($images);
// Options
$player_ads = zeta_compose_ad('_zetaflix_adplayer');
$player_wht = zeta_get_option('playsize','regular');
// Sidebar
$sidebar = zeta_get_option('sidebar_position_single','right');
// Dynamic Background

$watch_location = zeta_get_option('watch_location', 'same');

$ajaxep = zeta_get_option('playajaxep');
$ajaxep = (isset($ajaxep) && $post->post_type === 'seasons' && zeta_is_true('ajaxepdisplay','seasons')) ? true : null;

$episode = get_query_var('episode');

$tvssdata = zeta_get_option('tvssdata', 'default');
$tvssposter = zeta_get_option('tvssposter', 'default');

$tvshow = null;

if($tvssdata === 'inherit' && zeta_isset($postmeta,'serie') && zeta_isset($postmeta,'ids')){
	$args = array( 'fields' => 'ids', 'post_type' => 'tvshows', 'post_status' => 'publish', 'title' => zeta_isset($postmeta,'serie'), 'numberposts' => 1, 'meta_key'   => 'ids', 'meta_value' => zeta_isset($postmeta,'ids')); 
	$tvshow = zeta_transient_getposts('zetaflix_tvid_'.$post->ID, $args, MINUTE_IN_SECONDS*60);
	$tvshow = (isset($tvshow[0]) && is_numeric($tvshow[0])) ? $tvshow[0] : null;
}

$dataPid = ($tvssdata != 'default') ? $tvshow : $post->ID;
$imgId	= ($tvssposter != 'default') ? $tvshow : $post->ID;

$backdrop = omegadb_get_backdrop('episodes', $imgId, 'original');
$poster = omegadb_get_poster('seasons', $imgId);

?>
<?php zeta_breadcrumb( $post->ID, 'seasons', __z('Seasons')); ?>




<?php echo ($tvssdata === 'inherit' && !$tvshow && (current_user_can('editor') || current_user_can('administrator') )) ? '<div class="site-notice error">Season does not have a <span class="hl">parent tvshow</span>.</div>' : null; ?>
<?php  ZetaPlayer::viewer_ss($post->ID, zeta_isset($postmeta, 'tvshowid'), zeta_isset($postmeta, 'ids'), zeta_isset($postmeta, 'temporada'), $episode, $watch_location, $player, $trailer); ?>
<?php get_template_part('inc/parts/single/listas/seasons', null, array('ajaxep' => $ajaxep)); ?>
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
          <?php echo ($quality) ? '<span class="data-quality">'.strip_tags($quality).'</span>' : null;?>
          <?php if($d = get_post_meta($dataPid, 'imdbRating', true)) { ?>
            <span class="data-imdb v2">
                <?php echo __z('TMDb:').' '.$d;?>
                <b id="repimdb"></b>
        	    <?php if(current_user_can('administrator')) { ?><a data-id="<?php echo $post->ID; ?>" data-imdb="<?php echo zeta_isset($dataPid, 'ids'); ?>" id="update_imdb_rating"><i class="fa-solid fa-arrows-rotate"></i></a><?php } ?>
            </span>
          <?php }?>
          <?php 
		  if($d = get_post_meta($dataPid, 'episode_run_time', true)) echo "<span itemprop='duration' class='data-runtime'>{$d} ".__z('min')."</span>";?>
        </div>
        <div class="details-genre">
		<?php echo zeta_istax($dataPid, 'genres', '');?>

        </div>
        <div class="details-desc">
    			<p><?php echo get_the_content(null, false, $dataPid);?></p>
    		</div>
		<div class="details-info">
          <div class="info-col">
			<?php echo zeta_istax($dataPid, 'ztcreator', __('Creator'));?>
             <?php echo zeta_istax($dataPid, 'ztcast', __('Stars'));?>
			  <?php echo zeta_istax($dataPid, 'ztnetworks', __('Networks'));?>
          </div>
          <div class="info-col">
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
  
  <?php omegadb_get_gallery_images($images); ?>

  <?php if(ZETA_THEME_DOWNLOAD_MOD) get_template_part('inc/parts/single/links'); ?>

  <?php if(ZETA_THEME_RELATED) get_template_part('inc/parts/single/relacionados'); ?>

  <!-- Movie comments -->
  <?php get_template_part('inc/parts/comments'); ?>

  
  
  <div class="clearfix"></div>
</main>


<?php get_template_part('inc/parts/sidebar', null, array('ptyp' => 'seasons')); ?>

<div class="clearfix"></div>
</div>

