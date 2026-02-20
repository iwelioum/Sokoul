<?php
// Define defaults colors


/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('Pages'),
        'parent' => 'settings',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id' => 'page_heading',
                'type' => 'radio',
                'title' => __z('Page Heading'),
                'subtitle'    => __z('Display header image in pages'),
                'default' => 'none',
                'options' => array(
                    'none' => __z('None'),
                    'image'    => __z('Featured Image')
                )
            ),
		array(
                'type' => 'subheading',
                'content' => __z('Blog Pages')
            ),
            array(
                'id'      => 'pageblog',
                'type'    => 'select',
                'title'   => __z('Blog Archive'),
                'subtitle'    => __z('Set page to show the entries in blog'),
				'placeholder' => 'Select a page',
				'options'     => 'pages',
				'query_args'  => array(
				'posts_per_page' => -1 
				 )
            ),
            array(
                'id' => 'blog_heading',
                'type' => 'radio',
                'title' => __z('Blog Heading'),
                'subtitle'    => __z('Display header image in blog posts'),
                'default' => 'none',
                'options' => array(
                    'none' => __z('None'),
                    'image'    => __z('Featured Image')
                )
            ),
		array(
                'type' => 'subheading',
                'content' => __z('User Pages')
            ),
            array(
                'id'      => 'pageaccount',
                'type'    => 'select',
                'title'   => __z('Account'),
                'subtitle'    => __z('Set User Account page'),
				'placeholder' => 'Select a page',
				'options'     => 'pages',
				'query_args'  => array(
				'posts_per_page' => -1 
				 )
            ),
			
            array(
                'id' => 'pageaccount_display',
                'type' => 'radio',
                'title' => __z('Display As'),
                'default' => 'single',
                'options' => array(
                    'single' => __z('Single Page'),
                    'multi'    => __z('Multi Page')
                )
            ),

			array(
			  'id'        => 'pageaccount_subpages',
			  'type'      => 'fieldset',
			  'title'     => 'Sub-pages Url',
			  'fields'    => array(
				array(
					'id'    => 'pageaccount_list',
					'type'  => 'text',
					'title'      => __z('User List'),
					'attributes' => array(
						'placeholder' => 'list',
						'style' => 'width:200px'
					),
				),
				array(
					'id'    => 'pageaccount_seen',
					'type'  => 'text',
					'title'      => __z('Seen List'),
					'attributes' => array(
						'placeholder' => 'seen',
						'style' => 'width:200px'
					),
				),
				array(
					'id'    => 'pageaccount_links',
					'type'  => 'text',
					'title'      => __z('User Links'),
					'attributes' => array(
						'placeholder' => 'links',
						'style' => 'width:200px'
					),
				),
				array(
					'id'    => 'pageaccount_linkspending',
					'type'  => 'text',
					'title'      => __z('Pending Links'),
					'attributes' => array(
						'placeholder' => 'links-pending',
						'style' => 'width:200px'
					),
				),
				array(
					'id'    => 'pageaccount_settings',
					'type'  => 'text',
					'title'      => __z('Settings'),
					'attributes' => array(
						'placeholder' => 'settings',
						'style' => 'width:200px'
					),
				),
			  ),
			),
			
 
		
                        array(
                'type' => 'subheading',
                'content' => __z('Site Pages')
            ),
            array(
                'id'      => 'pagewatchplay',
                'type'    => 'select',
                'title'   => __z('Watch Page'),
                'subtitle'    => __z('Set play watch page'),
				'placeholder' => 'Select a page',
				'options'     => 'pages',
				'query_args'  => array(
				'posts_per_page' => -1 
				 )
            ),
            array(
                'id'      => 'pagetrending',
                'type'    => 'select',
                'title'   => __z('Trending'),
                'subtitle'    => __z('Set page to show trend content'),
				'placeholder' => 'Select a page',
				'options'     => 'pages',
				'query_args'  => array(
				'posts_per_page' => -1 
				 )
            ),
            array(
                'id'      => 'pageratings',
                'type'    => 'select',
                'title'   => __z('Ratings'),
                'subtitle'    => __z('Set page to show content rated by users'),
				'placeholder' => 'Select a page',
				'options'     => 'pages',
				'query_args'  => array(
				'posts_per_page' => -1 
				 )
            ),
            array(
                'id'      => 'pagecontact',
                'type'    => 'select',
                'title'   => __z('Contact'),
                'subtitle'    => __z('Set page to display the contact form'),
				'placeholder' => 'Select a page',
				'options'     => 'pages',
				'query_args'  => array(
				'posts_per_page' => -1 
				 )
            ),
            array(
                'id'      => 'pagetopimdb',
                'type'    => 'select',
                'title'   => __z('Top IMDb'),
                'subtitle'    => __z('Set page to show the best qualified content in IMDb'),
				'placeholder' => 'Select a page',
				'options'     => 'pages',
				'query_args'  => array(
				'posts_per_page' => -1 
				 )
            ),
            array(
                'id'      => 'pagetoprated',
                'type'    => 'select',
                'title'   => __z('Top Rated'),
                'subtitle'    => __z('Set page to show the most rated content'),
				'placeholder' => 'Select a page',
				'options'     => 'pages',
				'query_args'  => array(
				'posts_per_page' => -1 
				 )
            ),
            array(
                'id'      => 'pagerandom',
                'type'    => 'select',
                'title'   => __z('Random'),
                'subtitle'    => __z('Set page to show random contents'),
				'placeholder' => 'Select a page',
				'options'     => 'pages',
				'query_args'  => array(
				'posts_per_page' => -1 
				 )
            ),
            array(
                'id'      => 'pagethisyear',
                'type'    => 'select',
                'title'   => __z('This Year'),
                'subtitle'    => __z('Set page to show contents on current year'),
				'placeholder' => 'Select a page',
				'options'     => 'pages',
				'query_args'  => array(
				'posts_per_page' => -1 
				 )
            ),
        )
    )
);
