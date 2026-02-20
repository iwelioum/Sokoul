<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/

get_header(); 
$adarchive = zeta_compose_ad('_zetaflix_adarchive');
$adarchive2 = zeta_compose_ad('_zetaflix_adarchive2');
// Paged Info
$crrpage = get_query_var('page') ? get_query_var('page') : 1;
$perpage = zeta_get_option('bperpage','10');
// Recalculate Page
$tag_titl = single_cat_title('', false ); 
$tag_slug = ($tagslug = get_option('tag_base')) ? home_url().'/'.$tagslug.'/' : home_url().'/tags/';
$cat_slug = ($catslug = get_option('category_base')) ? home_url().'/'.$catslug.'/' : home_url().'/category/';

$tagsearch = new WP_Query(array(
			'showposts' => $perpage ,
			'paged' => $crrpage,
			'order' => 'DESC',
			'post_type' => 'post', 
			'tax_query' => array( 
				array(
					'taxonomy' => 'category', 
					'field'    => 'name',
					'terms'    => $tag_titl, 
				),
			) 
		)
	);

$sidebar = zeta_get_option('sidebar_position_archives','right');
 ?>
<main>

<div class="display-page-heading">
<?php echo '<h3>'.__z('Tag: ').' "<span class="search-key">'.$tag_titl.'</span>"</h3>';
if(!$tagsearch->have_posts()){
	echo '<p class="no-result">'.__z('No Content Available').'</p>';
}?>
</div>
<div class="display-page result tags">
         <div class="blog-archive results">
		 <?php 	if($adarchive) echo '<div class="content-ads module-archive-ads">'.$adarchive.'</div>';?>
		
		 			<?php if ($tagsearch->have_posts()) : while ( $tagsearch->have_posts() ) : $tagsearch->the_post();
					$cats = get_the_terms($post->ID, 'category');
					$tags = get_the_terms($post->ID, 'post_tag');
			?>
          <div class="blog-post">
            <div class="post-thumb">
			
              <?php echo zetaflix_get_blog_thumb($post->ID);?>
            </div>
            <div class="post-data">
              <div class="post-title">
                <a href="<?php the_permalink(); ?>"><h3><?php the_title();?></h3></a>
              </div>
              <div class="post-meta">
                <a href="#"><i class="fa-solid fa-user"></i> <span class="meta-user"><?php the_author(); ?></span></a>
                <a href="#"><i class="fa-solid fa-calendar-days"></i> <span class="meta-date"><?php zeta_post_date('F j, Y'); ?></span></a>
              </div>
              <div class="post-excerpt">
                <p><?php zt_content_alt('180'); ?></p>
              </div>
              <a href="<?php the_permalink(); ?>" class="post-more"><?php _z('Read More');?></a>
              <div class="post-terms">
			  <?php if($cats && is_array($cats)){?>
                <ul class="terms-cat">
				<li class="term-cat-title"><i class="fa-solid fa-film"></i> <span><?php _z('Category');?>: </span></li>
				<?php  foreach($cats as $cat){?>
                 <li><a href="<?php echo $cat_slug.$cat->slug;?>"><?php echo $cat->name;?></a></li>
				<?php }?>
                </ul>
				<?php }?>
				<?php if($tags && is_array($tags)){?>
                <ul class="terms-tags">
                  <li class="term-tags-title"><i class="fa-solid fa-tags"></i> <span class="term-cat-title"><?php _z('Tags');?> </span></li>
				  <?php  foreach($tags as $tag){?>
				  
                  <li><a href="<?php echo $tag_slug.$tag->slug;?>"><?php echo $tag->name;?></a></li>
                  <?php }?>
                 </ul>
				<?php }?>
              </div>
            </div>


          </div>
         
			<?php endwhile; ?>
			
			 <?php	zeta_blog_pagination( '', $tagsearch);?>
			<?php else: ?>
			<div class="zt-no-post"><?php _z('No posts to show'); ?></div>
			<?php endif; wp_reset_query(); ?>
			<?php 	if($adarchive2) echo '<div class="content-ads module-archive-ads">'.$adarchive2.'</div>';?>
			<?php ?>
		 </div>
</div>
      <div class="clearfix"></div>
    </main>
	
	

<?php get_template_part('inc/parts/sidebar', null, array('blog_arch' => true));?>

<?php get_footer(); ?>
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 
 