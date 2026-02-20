<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
*
* @since 1.0.0
*
*/
get_header();
 ?>
 <main>
<div class="display-page-heading requests-head"><h3><?php _z('Requests'); ?></h3> <span class="request-count"><?php echo zeta_total_count('requests'); ?></span></div>
<div class="display-page requests">
	<nav class="requests_main_nav">
		<a id="discoverclic" for="iterm" class="add_request"><?php _z('+ Add new'); ?></a>
		<a id="closediscoverclic" class="add_request hidde"><?php _z('Go back'); ?></a>
		<ul class="requests_menu_filter hidde">
			<li class="filtermenu"><a data-type="movie" class="active"><?php _z('Movies'); ?></a></li>
			<li class="filtermenu"><a data-type="tv"><?php _z('TVShows'); ?></a></li>
		</ul>
		<ul class="requests_menu">
			<li class="rmenu"><a data-tab="listrequests" class="active"><?php _z('All'); ?></a></li>
		</ul>
		<div class="clearfix"></div>
	</nav>
	<div id="discover" class="discover hidde">
		<div class="fixbox">
			<div class="box animation-1">
				<form id="discover_requests">
					<input type="text" id="term" name="term" placeholder="<?php _z('Search a title..'); ?>" autocomplete="off">
					<input type="hidden" id="type" name="type" value="movie">
					<input type="hidden" id="nonce" name="nonce" value="<?php echo wp_create_nonce('omegadb_requests_users'); ?>">
					<input type="hidden" id="action" name="action" value="omegadb_requests_search">
					<input type="hidden" id="page" name="page" value="1">
					<button class="filter" id="get_requests" type="submit"><span class="fas fa-search"></span></button>
				</form>
			</div>
		</div>
		<div id="discover_results" class="discover_results content">
			<div class="metainfo"><?php _z('Find a title you want to suggest'); ?></div>
		</div>
	</div>
    <div class="post_request">
		<div id="post_request_archive" class="box_post"></div>
	</div>
	<div id="requests" class="content">
		<div class="tabox current" id="listrequests">
			<div class="items <?php echo $maxwidth;?>">
            <?php if (have_posts()) { while (have_posts()) { the_post(); $meta = zeta_get_postmeta('_dbmv_requests_post'); ?>

<div id="item-<?php the_ID(); ?>" class="display-item">
	<div class="item-box">
	      <a class="ml-mask jt" data-hasqtip="112" oldtitle="<?php the_title(); ?>" title="<?php the_title(); ?>"></a>

	   
	   <img data-original="<?php echo 'https://image.tmdb.org/t/p/w185'. $meta['poster']; ?>" class="thumb mli-thumb" alt="<?php the_title(); ?>" src="<?php echo 'https://image.tmdb.org/t/p/w185'. $meta['poster']; ?>">
	   
	      <div class="item-desc">
	<?php if($titl = get_the_title()){?>
	  <div class="item-desc-title">
		<h3><?php echo $titl;?></h3>
	  </div>
	<?php }?>
	
	<?php if(isset($data_qual)){?>
      <div class="item-desc-hl">
         <div class="desc-hl-right">
			<?php echo ($quality) ? '<span class="item-quality">'.strip_tags($quality).'</span>' : null; ?>
         </div>
      </div>
	<?php }?>
   </div>
   </div>
</div>




                <?php } } ?>
					<div class="clearfix"></div>
			</div>
		</div>
        <?php zeta_pagination(); ?>
		<div class="tabox" id="addrequests"></div>
	
	</div>
</div>
</main>
<?php get_footer();?>
